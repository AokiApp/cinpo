package app.aoki.cinpo.runtime.jcresim;

import java.util.List;
import java.util.Locale;

/**
 * Host-platform helpers needed to stage the Oracle JCDK simulator binary.
 *
 * <p>These helpers are jcresim-specific: they describe which simulator build to
 * bundle, which executable it uses, and which files ship with it. Other runtimes
 * (pcsc, jcardengine) have no equivalent concept and do not need these values.
 */
final class JcreSimPlatform {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    private static final String OS_ARCH = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

    private JcreSimPlatform() {
    }

    static String detectOS() {
        if (OS_NAME.contains("linux")) return "linux";
        if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) return "darwin";
        if (OS_NAME.contains("win")) return "windows";
        throw new IllegalStateException("Unsupported operating system: " + OS_NAME);
    }

    static String detectArch() {
        if (OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm64")) return "aarch64";
        if (OS_ARCH.contains("amd64") || OS_ARCH.contains("x86_64")) return "amd64";
        throw new IllegalStateException("Unsupported CPU architecture: " + OS_ARCH);
    }

    static boolean isWindows() {
        return "windows".equals(detectOS());
    }

    /**
     * Returns the Oracle JCDK simulator executable name for the current OS.
     * Oracle names the binary {@code jcsl} on Linux/macOS and {@code jcsw.exe} on Windows.
     *
     * @return the simulator executable file name
     */
    static String simulatorExecutableName() {
        return isWindows() ? "jcsw.exe" : "jcsl";
    }

    /**
     * Returns the shared-library files that accompany the simulator executable on the
     * current OS. Used to stage the runtime out of the appliance JAR.
     *
     * @return companion native library file names
     */
    static List<String> simulatorRuntimeLibraryNames() {
        if (isWindows()) {
            return List.of("legacy.dll", "libcrypto-3.dll", "libssl-3.dll");
        }
        return List.of("legacy.so", "libcrypto.so", "libcrypto.so.3", "libssl.so", "libssl.so.3");
    }
}
