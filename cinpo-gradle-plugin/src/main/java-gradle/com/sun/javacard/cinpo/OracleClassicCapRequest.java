package com.sun.javacard.cinpo;

import app.aoki.cinpo.config.AppletManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Request for the Oracle classic Java Card CAP builder.
 *
 * <p>This class intentionally lives under {@code com.sun.javacard.cinpo}. The
 * builder coordinates Oracle Java Card internal packages and same-package helper
 * logic. Keeping the request here prevents {@code app.aoki.*} Gradle code from
 * directly depending on the converter, JCASM parser, linker, and CAP writer types.</p>
 */
public record OracleClassicCapRequest(
        AppletManifest manifest,
        List<Path> companionExportDirectories,
        Path compiledClassesRoot,
        Path toolLibraryDirectory,
        Path workDirectory,
        Path stagedCapFile
) {
    public OracleClassicCapRequest {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(companionExportDirectories, "companionExportDirectories");
        Objects.requireNonNull(compiledClassesRoot, "compiledClassesRoot");
        Objects.requireNonNull(toolLibraryDirectory, "toolLibraryDirectory");
        Objects.requireNonNull(workDirectory, "workDirectory");
        Objects.requireNonNull(stagedCapFile, "stagedCapFile");
        companionExportDirectories = List.copyOf(companionExportDirectories);
    }
}
