package com.sun.javacard.converter;

import app.aoki.cinpo.config.AppletEntry;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * CINPO-owned converter logic placed in {@code com.sun.javacard.converter}.
 *
 * <p>The package is intentional: it lets the code stay typed and close to Oracle
 * converter data structures instead of routing through CLI-shaped APIs or using
 * reflection. The Gradle-facing code does not call this class directly; it is an
 * implementation detail of {@code com.sun.javacard.cinpo.OracleClassicCapBuilder}.</p>
 */
public final class CinpoConverterLogic {
    private CinpoConverterLogic() {
    }

    /**
     * Builds the mutable Oracle conversion profile directly from CINPO manifest data.
     *
     * <p>The converter's profile object is intentionally populated here rather than
     * through {@code OptionParser}. That keeps CAP generation independent from the
     * CLI-shaped API while preserving the same internal representation expected by
     * {@link Converter}.</p>
     */
    public static ConversionProfile profileFor(
            AppletManifest manifest,
            List<Path> companionExportDirectories,
            Path compiledClassesRoot,
            Path outputRoot,
            Path toolLibraryDirectory
    ) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(companionExportDirectories, "companionExportDirectories");

        ConversionProfile profile = new ConversionProfile();
        profile.APIExpDir = "api_export_files_" + manifest.targetApiVersion();
        profile.target22 = "3.0.4".equals(manifest.targetApiVersion()) || "3.0.5".equals(manifest.targetApiVersion());

        // Support Java Card's int-capable converter mode. CINPO does not currently
        // expose a manifest switch for this, and enabling it is the safer default
        // for real-world applets that may use integer operations.
        profile.int_supported = true;

        // The manifest describes a fresh package build. We are not upgrading from
        // a pre-existing EXP file for the same package, so binary compatibility
        // checks against "this" package are intentionally disabled.
        profile.this_exp_provided = false;

        // Keep generated artifacts lean and deterministic: no debug component and
        // no maskgen/native-method mode unless a future manifest field explicitly
        // requests them.
        profile.debug = false;
        profile.mask = false;

        // CAP generation should fail fast if verifier rejects the archive. This
        // mirrors converter default behavior and catches invalid applet output before
        // it reaches GlobalPlatform installation.
        profile.noVerify = false;

        // Emit all artifacts used by the wrapperless pipeline: JCA feeds the CAP
        // writer, EXP feeds verifier/export-manager state, and CAP is the final
        // runtime payload copied to cap/<packageName>.cap.
        profile.output = ConversionProfile.OUTPUT_CAP_FILE
                | ConversionProfile.OUTPUT_EXP_FILE
                | ConversionProfile.OUTPUT_JCA_FILE;
        profile.export_path = exportPaths(manifest, companionExportDirectories, toolLibraryDirectory);
        profile.class_root = compiledClassesRoot;
        profile.classes = classFiles(compiledClassesRoot, manifest.packageName()).stream()
                .map(Path::toFile)
                .toArray(File[]::new);
        profile.output_dir = outputRoot;
        profile.package_profile = packageProfile(manifest);
        profile.applets_profile = appletProfiles(manifest.applets());

        // Export generation only happens for public packages. CINPO's runtime and
        // verifier path expect the generated EXP to be available, so public package
        // output is part of the build contract for now.
        profile.publicPackage = true;

        // Suppress Oracle banner text in Gradle output; task logging should own the
        // user-facing progress messages.
        profile.nobanner = true;
        return profile;
    }

    /** Runs the compact converter phase without Main, ConverterHarness or OptionParser. */
    public static Converter convertCompact(ConversionProfile profile) throws ConverterException, IOException {
        Converter converter = new Converter(profile, new Hashtable<String, File>());

        // Oracle's embedded API-export resource lookup is tied to the jar that
        // defines its converter classes. CINPO deliberately keeps that jar in the
        // consumer's vendor cache instead of flattening it into the Maven plugin,
        // so preload the extracted EXP files explicitly. This also makes AID-based
        // imports available before package-name resolution occurs.
        for (Path exportRoot : profile.export_path) {
            if (!Files.isDirectory(exportRoot)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(exportRoot)) {
                for (Path exportFile : stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".exp"))
                        .sorted()
                        .toList()) {
                    converter.getExportFileManager().buildExportFile(exportFile.toFile(), exportFile.toString());
                }
            }
        }

        converter.convert(0, null, 0);
        return converter;
    }

    private static Path[] exportPaths(AppletManifest manifest, List<Path> companionExportDirectories, Path toolLibraryDirectory) {
        List<Path> exportPaths = new ArrayList<>();

        Path apiExports = toolLibraryDirectory.resolve("api_export_files_" + manifest.targetApiVersion());
        if (Files.isDirectory(apiExports)) {
            exportPaths.add(apiExports);
        }

        exportPaths.addAll(companionExportDirectories);

        // tools.jar embeds API exports and ExportFileManager can resolve standard API
        // packages internally. Keep export_path empty rather than inventing a wrong
        // filesystem path when the unpacked export tree is absent.
        return exportPaths.toArray(Path[]::new);
    }

    private static PackageProfile packageProfile(AppletManifest manifest) {
        byte[] version = parseVersion(manifest.version());
        PackageProfile profile = new PackageProfile();

        // Oracle's OptionParser stores package names in VM-internal slash form.
        // Keeping the manifest-facing name dotted is correct for CINPO resource
        // layout, but the converter's JPackage comparison expects this field to
        // match classfile package names after '/' normalization.
        profile.package_name = slashName(manifest.packageName());
        profile.major_version = version[0];
        profile.minor_version = version[1];
        profile.aid = manifest.loadFileAid();
        return profile;
    }

    private static AppletProfile[] appletProfiles(List<AppletEntry> applets) {
        AppletProfile[] profiles = new AppletProfile[applets.size()];
        for (int i = 0; i < applets.size(); i++) {
            AppletEntry entry = applets.get(i);
            AppletProfile profile = new AppletProfile();

            // Match Oracle's CLI parser behavior: applet install classes are stored
            // with slash separators before Converter normalizes/uses them.
            profile.install_class = slashName(entry.className());
            profile.aid = entry.classAid();
            profiles[i] = profile;
        }
        return profiles;
    }

    private static List<Path> classFiles(Path compiledClassesRoot, String packageName) throws IOException {
        Path packageRoot = compiledClassesRoot.resolve(packageName.replace('.', File.separatorChar));
        Path searchRoot = Files.isDirectory(packageRoot) ? packageRoot : compiledClassesRoot;
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            List<Path> classes = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList();
            if (classes.isEmpty()) {
                throw new JavaCardToolException("No compiled applet class files found under " + searchRoot);
            }
            return classes;
        }
    }

    private static String slashName(String binaryName) {
        return binaryName.replace('.', '/');
    }

    private static byte[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 2) {
            throw new JavaCardToolException("Java Card package version must be major.minor: " + version);
        }
        return new byte[] { (byte) Integer.parseInt(parts[0]), (byte) Integer.parseInt(parts[1]) };
    }

}
