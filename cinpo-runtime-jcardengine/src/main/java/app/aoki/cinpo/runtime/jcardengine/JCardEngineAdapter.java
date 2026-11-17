package app.aoki.cinpo.runtime.jcardengine;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.config.AppletEntry;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.util.Util;
import apdu4j.core.BIBO;
import apdu4j.core.BIBOException;
import com.licel.jcardsim.utils.AIDUtil;
import java.util.Objects;
import javacard.framework.AID;
import javacard.framework.Applet;
import pro.javacard.engine.JavaCardEngine;

/**
 * JCardEngine adapter that keeps one fresh engine instance per CINPO run.
 */
public final class JCardEngineAdapter {

    /**
     * Create an APDU channel backed by a fresh JCardEngine instance.
     *
     * @return an APDU channel implementation
     */
    public ApduChannel createChannel() {
        return new JCardEngineChannel(createEngine());
    }

    private static JavaCardEngine createEngine() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader effectiveLoader = contextLoader != null
                ? contextLoader
                : JCardEngineAdapter.class.getClassLoader();
        return new JavaCardEngine.Builder()
                .withClassLoader(effectiveLoader)
                .build();
    }

    /**
     * Concrete JCardEngine-backed channel. Installation is performed by the
     * JCardEngine-specific install phase before host-side tasks run, using
     * class name plus instance AID as the authoritative CLASS-BOOTSTRAP inputs.
     */
    public static final class JCardEngineChannel implements ApduChannel {

        private final Object monitor = new Object();
        private final JavaCardEngine engine;
        private BIBO session;

        JCardEngineChannel(JavaCardEngine engine) {
            this.engine = Objects.requireNonNull(engine);
        }

        public void install(AppletManifest manifest) {
            Objects.requireNonNull(manifest);

            synchronized (monitor) {
                invalidateSession();
                for (AppletEntry applet : manifest.applets()) {
                    installApplet(applet);
                }
            }
        }

        @Override
        public ResponseApdu transmit(CommandApdu capdu) {
            Objects.requireNonNull(capdu);

            synchronized (monitor) {
                try {
                    byte[] response = ensureSession().transceive(capdu.toBytes());
                    return ResponseApdu.fromBytes(response);
                } catch (BIBOException e) {
                    invalidateSession();
                    throw new IllegalStateException("Failed to transmit APDU via JCardEngine", e);
                } catch (RuntimeException e) {
                    invalidateSession();
                    throw new IllegalStateException("JCardEngine transport failed during APDU exchange", e);
                }
            }
        }

        @Override
        public void reset() {
            synchronized (monitor) {
                invalidateSession();
            }
        }

        @Override
        public void close() {
            synchronized (monitor) {
                invalidateSession();
            }
        }

        private void installApplet(AppletEntry applet) {
            requireZeroPrivileges(applet);
            Class<? extends Applet> appletClass = resolveAppletClass(applet.className());
            AID instanceAid = AIDUtil.create(applet.instanceAid());
            engine.installApplet(instanceAid, appletClass, new byte[0]);
        }

        private static void requireZeroPrivileges(AppletEntry applet) {
            byte[] privileges = applet.privileges();
            for (byte privilege : privileges) {
                if (privilege != 0x00) {
                    throw new IllegalStateException(
                            "JCardEngine runtime currently supports only zero applet privileges; applet '"
                                    + applet.id() + "' declared privileges " + Util.toHex(privileges));
                }
            }
        }

        private static Class<? extends Applet> resolveAppletClass(String className) {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader effectiveLoader = contextLoader != null
                    ? contextLoader
                    : JCardEngineChannel.class.getClassLoader();
            try {
                Class<?> rawClass = Class.forName(className, true, effectiveLoader);
                return rawClass.asSubclass(Applet.class);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Applet class not found for JCardEngine runtime: " + className, e);
            } catch (ClassCastException e) {
                throw new IllegalStateException(
                        "Configured class is not a javacard.framework.Applet for JCardEngine runtime: " + className,
                        e);
            }
        }

        private BIBO ensureSession() {
            if (session == null) {
                session = engine.connect("*", true);
            }
            return session;
        }

        private void invalidateSession() {
            if (session != null) {
                session.close();
                session = null;
            }
        }
    }
}
