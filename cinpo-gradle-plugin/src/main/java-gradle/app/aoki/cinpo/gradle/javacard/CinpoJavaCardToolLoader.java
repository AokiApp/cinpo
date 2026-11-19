package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads the Oracle Java Card tools in a classloader private to one CAP build.
 *
 * <p>CINPO's bridge classes live in {@code com.sun.javacard.*} so they can use
 * package-private Oracle APIs without reflection. That only works when the bridge
 * and the Oracle classes are defined by the same classloader. The previous
 * implementation pushed {@code tools.jar} into the Gradle plugin classloader with
 * {@code addURL}; that loader is shared by every build in the daemon, so two
 * projects with different Java Card SDKs could observe each other's classes and
 * the URL stayed behind after the build.</p>
 *
 * <p>This loader instead creates a child loader per build. It delegates everything
 * outside {@code com.sun.javacard.*} to the plugin classloader, including the
 * {@code app.aoki.*} request and result types, so the plugin can call the builder
 * through ordinary typed code while Oracle classes stay isolated.</p>
 */
final class CinpoJavaCardToolLoader implements AutoCloseable {

    private static final String BUILDER_CLASS_NAME = "com.sun.javacard.cinpo.OracleClassicCapBuilder";
    private static final String BUILD_METHOD_NAME = "build";
    private static final String TOOL_PACKAGE_PREFIX = "com.sun.javacard.";

    private final URLClassLoader loader;

    private CinpoJavaCardToolLoader(URLClassLoader loader) {
        this.loader = loader;
    }

    /**
     * Opens a loader for one {@code tools.jar}.
     *
     * @param toolsJar Oracle Java Card tool jar extracted by the prepare task
     * @return a loader that must be closed after the build
     * @throws IOException if the jar is missing or its URL cannot be formed
     */
    static CinpoJavaCardToolLoader open(Path toolsJar) throws IOException {
        Objects.requireNonNull(toolsJar, "toolsJar");
        if (!Files.isRegularFile(toolsJar)) {
            throw new JavaCardToolException("Java Card tools.jar not found: " + toolsJar);
        }

        List<URL> urls = new ArrayList<>();

        URL bridgeLocation = bridgeLocation();
        if (bridgeLocation == null) {
            throw new JavaCardToolException(
                    "CINPO cannot locate its own Java Card bridge classes. The plugin must run from a jar or "
                            + "a class directory so com.sun.javacard.cinpo can share a classloader with tools.jar.");
        }
        // The bridge comes first so CINPO's own com.sun.javacard.cinpo classes always
        // win over anything a future tools.jar might add under the same names.
        urls.add(bridgeLocation);
        urls.add(toolsJar.toUri().toURL());

        ClassLoader parent = CinpoJavaCardToolLoader.class.getClassLoader();
        return new CinpoJavaCardToolLoader(
                new ToolClassLoader(urls.toArray(URL[]::new), parent));
    }

    private static URL bridgeLocation() {
        if (CinpoJavaCardToolLoader.class.getProtectionDomain() == null
                || CinpoJavaCardToolLoader.class.getProtectionDomain().getCodeSource() == null) {
            return null;
        }
        return CinpoJavaCardToolLoader.class.getProtectionDomain().getCodeSource().getLocation();
    }

    /**
     * Runs one CAP build inside the isolated loader.
     *
     * @param request plugin-side request shared with the builder
     * @return paths to the Oracle output and the staged runtime resource
     * @throws Exception whatever the builder throws, unwrapped from reflection
     */
    CapBuildResult build(CapBuildRequest request) throws Exception {
        Objects.requireNonNull(request, "request");
        Class<?> builderClass = Class.forName(BUILDER_CLASS_NAME, true, loader);
        Method buildMethod = builderClass.getMethod(BUILD_METHOD_NAME, CapBuildRequest.class);
        try {
            return (CapBuildResult) buildMethod.invoke(null, request);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }

    /**
     * Returns the classloader that owns the Oracle packages.
     *
     * <p>Package-private so tests can assert which loader defines a class without
     * widening the plugin's public surface.</p>
     *
     * @return the isolated loader
     */
    ClassLoader classLoader() {
        return loader;
    }

    /**
     * Child-first loader for the Oracle packages.
     *
     * <p>Only {@code com.sun.javacard.*} is resolved from the local URLs. Service
     * provider interfaces and CINPO types keep the plugin classloader's identity,
     * which is what lets the reflective call return a typed result.</p>
     */
    private static final class ToolClassLoader extends URLClassLoader {

        ToolClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(TOOL_PACKAGE_PREFIX)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Fall back to the parent so a class that only the plugin
                        // classpath provides still resolves instead of disappearing.
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
