package app.aoki.cinpo.runtime.jcresim;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.apdu.CommandApdu;
import app.aoki.cinpo.apdu.ResponseApdu;
import app.aoki.cinpo.util.Util;
import com.oracle.smartcardio.socket.SocketCardTerminalProvider;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * JCRE simulator adapter that manages the Oracle jcsl simulator process lifecycle.
 */
public final class JcreSimAdapter {

    private static final String TERMINAL_FACTORY_TYPE = "SocketCardTerminalFactoryType";
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String CONNECT_PROTOCOL = "*";

    /**
     * Create an APDU channel backed by the JCRE simulator.
     *
     * @return an APDU channel implementation
     */
    public ApduChannel createChannel() {
        return new JcreSimChannel();
    }

    /**
     * Resolve the jcsl simulator executable using automatic discovery.
     * <p>
     * Discovery order:
     * <ol>
     *   <li>build/cinpo-cache/jcsl/&lt;os&gt;-&lt;arch&gt;/jcsl</li>
     *   <li>build/cinpo-cache/jcdk/simulator/runtime/bin/jcsl</li>
     * </ol>
     *
     * @return path to the jcsl executable
     * @throws IllegalStateException if the simulator executable is not found
     */
    static Path resolveSimulatorExecutable() {
        String configuredPath = System.getProperty("cinpo.simulator.path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configuredSimulator = Path.of(configuredPath);
            if (Files.isExecutable(configuredSimulator)) {
                return configuredSimulator;
            }
        }

        Path bundledPath = extractBundledSimulatorIfPresent();
        if (bundledPath != null && Files.isExecutable(bundledPath)) {
            return bundledPath;
        }

        Path buildCachePath = findJcslInBuildCache();
        if (buildCachePath != null && Files.isExecutable(buildCachePath)) {
            return buildCachePath;
        }

        String executableName = Util.simulatorExecutableName();
        Path managedSimulator = Path.of("build").resolve("cinpo-cache").resolve("jcdk")
                .resolve("simulator").resolve("runtime").resolve("bin").resolve(executableName);
        if (Files.isExecutable(managedSimulator)) {
            return managedSimulator;
        }

        throw new IllegalStateException(
                "Oracle JCRE simulator executable (" + executableName + ") not found. Searched locations:\n" +
                "  - cinpo.simulator.path=" + (configuredPath == null ? "<unset>" : configuredPath) + "\n" +
                "  - build/cinpo-cache/jcsl/" + Util.detectOS() + "-" + Util.detectArch() + "/" + executableName + "\n" +
                "  - build/cinpo-cache/jcdk/simulator/runtime/bin/" + executableName + "\n" +
                "Configure cinpo.simulator.path or prepare the managed JCDK build cache first.");
    }

    private static Path extractBundledSimulatorIfPresent() {
        String os = Util.detectOS();
        String arch = Util.detectArch();
        String executableName = Util.simulatorExecutableName();
        String resourcePrefix = "cinpo-simulator/" + os + "-" + arch + "/";
        String markerResource = resourcePrefix + executableName;
        ClassLoader loader = JcreSimAdapter.class.getClassLoader();
        if (loader.getResource(markerResource) == null) {
            return null;
        }

        List<String> stagedFiles = new ArrayList<>();
        stagedFiles.add(executableName);
        stagedFiles.addAll(Util.simulatorRuntimeLibraryNames());

        Path sharedDir = Path.of(System.getProperty("java.io.tmpdir"), "cinpo-simulator", os + "-" + arch);
        try {
            return stageBundledSimulator(loader, resourcePrefix, stagedFiles, sharedDir, executableName);
        } catch (IOException sharedStagingFailed) {
            // Windows refuses to replace an executable or DLL that another process still
            // holds open, so a concurrent or recently finished run can make the shared
            // location unwritable. Stage into a directory private to this JVM instead.
            try {
                Path privateDir = Files.createTempDirectory("cinpo-simulator-");
                return stageBundledSimulator(loader, resourcePrefix, stagedFiles, privateDir, executableName);
            } catch (IOException privateStagingFailed) {
                privateStagingFailed.addSuppressed(sharedStagingFailed);
                throw new IllegalStateException(
                        "Failed to extract bundled CINPO simulator runtime", privateStagingFailed);
            }
        }
    }

    /**
     * Copy the bundled simulator runtime into {@code targetDir}, reusing files that are
     * already staged there so a locked-but-identical binary does not have to be replaced.
     *
     * @return path to the staged simulator executable
     */
    private static Path stageBundledSimulator(
            ClassLoader loader,
            String resourcePrefix,
            List<String> stagedFiles,
            Path targetDir,
            String executableName) throws IOException {
        Files.createDirectories(targetDir);
        for (String name : stagedFiles) {
            URL resource = loader.getResource(resourcePrefix + name);
            if (resource == null) {
                continue;
            }
            Path target = targetDir.resolve(name);
            if (isAlreadyStaged(resource, target)) {
                continue;
            }
            try (InputStream in = resource.openStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().setExecutable(true);
        }
        return targetDir.resolve(executableName);
    }

    /**
     * Decide whether a previously staged file can be reused. The bundled runtime is a
     * fixed set of Oracle binaries for one kit version, so a size match identifies the
     * same file and rules out a truncated copy from an interrupted run.
     */
    private static boolean isAlreadyStaged(URL resource, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        long resourceSize = resource.openConnection().getContentLengthLong();
        return resourceSize >= 0 && resourceSize == Files.size(target);
    }

    /**
     * Find the simulator executable in the build cache using OS and architecture detection.
     *
     * @return path to the simulator executable in build cache, or null if not found
     */
    private static Path findJcslInBuildCache() {
        String os = Util.detectOS();
        String arch = Util.detectArch();
        Path buildCache = Path.of("build").resolve("cinpo-cache").resolve("jcsl");
        return buildCache.resolve(os + "-" + arch).resolve(Util.simulatorExecutableName());
    }

    /**
     * APDU channel implementation backed by the JCRE simulator.
     */
    private static final class JcreSimChannel implements ApduChannel {

        private final Object monitor = new Object();
        private final SimulatorProcess simulatorProcess;

        private Card card;
        private CardChannel basicChannel;

        JcreSimChannel() {
            Path executable = resolveSimulatorExecutable();
            this.simulatorProcess = new SimulatorProcess(executable);
        }

        @Override
        public ResponseApdu transmit(CommandApdu capdu) {
            Objects.requireNonNull(capdu);

            synchronized (monitor) {
                try {
                    CardChannel channel = ensureBasicChannel();
                    javax.smartcardio.ResponseAPDU rapdu = channel.transmit(new CommandAPDU(capdu.toBytes()));
                    return ResponseApdu.fromBytes(rapdu.getBytes());
                } catch (CardException e) {
                    invalidateConnection();
                    throw new IllegalStateException("Failed to transmit APDU to the Oracle JCRE simulator", e);
                } catch (IllegalStateException e) {
                    invalidateConnection();
                    throw e;
                } catch (RuntimeException e) {
                    invalidateConnection();
                    throw new IllegalStateException(
                            "Oracle JCRE simulator transport failed during APDU exchange. " +
                            "If the simulator log contains 'Binary is not configured with initial SCP keys', " +
                            "configure a project-local copied jcsl binary with Oracle Configurator using keys " +
                            "matching the jcdksim profile. CINPO does not modify the original JCDK binary.", e);
                }
            }
        }

        @Override
        public void reset() {
            synchronized (monitor) {
                try {
                    if (card != null) {
                        card.disconnect(true);
                    }
                } catch (CardException e) {
                    throw new IllegalStateException("Failed to reset the Oracle JCRE simulator session", e);
                } finally {
                    invalidateConnection();
                }
            }
        }

        @Override
        public void close() {
            synchronized (monitor) {
                try {
                    if (card != null) {
                        card.disconnect(false);
                    }
                } catch (CardException e) {
                    throw new IllegalStateException("Failed to close the Oracle JCRE simulator session", e);
                } finally {
                    invalidateConnection();
                    simulatorProcess.close();
                }
            }
        }

        private CardChannel ensureBasicChannel() throws CardException {
            if (basicChannel != null) {
                return basicChannel;
            }

            simulatorProcess.ensureReady();
            card = connectCard(simulatorProcess.getPort());
            basicChannel = card.getBasicChannel();
            return basicChannel;
        }

        private Card connectCard(int port) throws CardException {
            try {
                SocketCardTerminalProvider terminalProvider = createSocketCardTerminalProvider();
                SocketAddress address = new InetSocketAddress(LOOPBACK_HOST, port);
                TerminalFactory terminalFactory = TerminalFactory.getInstance(
                        TERMINAL_FACTORY_TYPE,
                        List.of(address),
                        terminalProvider);
                return terminalFactory.terminals().list().getFirst().connect(CONNECT_PROTOCOL);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(
                        "Oracle socket terminal provider is not available on the classpath", e);
            }
        }

        private static SocketCardTerminalProvider createSocketCardTerminalProvider() {
            return new SocketCardTerminalProvider();
        }

        private void invalidateConnection() {
            basicChannel = null;
            card = null;
        }
    }
}
