package app.aoki.cinpo.gradle.javacard;

import app.aoki.cinpo.config.AppletManifest;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Defines where a generated CAP file must live on the runtime classpath.
 *
 * <p>The runtime install phase builds the resource name as
 * {@code cap/<packageName>.cap}. Keeping that rule here prevents the Gradle task
 * and runtime phase from drifting apart.</p>
 *
 * <p>Do not confuse this dotted resource name with Oracle converter internals.
 * CINPO runtime lookup intentionally uses the manifest package name unchanged,
 * for example {@code cap/app.aoki.example.cap}. The Oracle converter, meanwhile,
 * writes its temporary output below slash-separated package directories such as
 * {@code app/aoki/example/javacard/example.cap}. This class owns only the CINPO
 * classpath-facing form.</p>
 */
public record CapResourceLayout(String resourcePath, Path resourceFile) {
    public CapResourceLayout {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(resourceFile, "resourceFile");
    }

    /**
     * Creates the runtime CAP resource layout for one manifest.
     *
     * <p>For a manifest package {@code app.aoki.cinpo.example}
     * and generated resource root {@code build/generated/cinpo/resources/main}, this
     * returns resource path {@code cap/app.aoki.cinpo.example.cap} and file path
     * {@code build/generated/cinpo/resources/main/cap/app.aoki.cinpo.example.cap}.</p>
     */
    public static CapResourceLayout forManifest(AppletManifest manifest, Path generatedResourcesRoot) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(generatedResourcesRoot, "generatedResourcesRoot");

        String resourcePath = "cap/" + manifest.packageName() + ".cap";
        return new CapResourceLayout(resourcePath, generatedResourcesRoot.resolve("cap").resolve(manifest.packageName() + ".cap"));
    }
}
