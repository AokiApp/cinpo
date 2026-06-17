package com.sun.javacard.cinpo;

import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.gradle.javacard.tools.JavaCardToolException;
import com.sun.javacard.converter.CinpoConverterLogic;
import com.sun.javacard.converter.ConversionProfile;
import com.sun.javacard.converter.Converter;
import com.sun.javacard.jcasm.CinpoJcasmLogic;
import com.sun.javacard.jcasm.JCPackage;
import com.sun.javacard.jcasm.cap.CinpoCapgenLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * High-level wrapperless builder for classic Java Card CAP files.
 *
 * <p>This class is the single entry point that CINPO Gradle code calls. It lives
 * in the Oracle namespace because it is a small domain layer over Oracle Java
 * Card internals: converter profiles, JCASM parsing, CAP linking, CAP writing,
 * and off-card verification. The unusual package placement is intentional and
 * keeps low-level Oracle mechanics out of {@code app.aoki.*} code.</p>
 *
 * <p>The implementation does not call {@code Main.main(...)},
 * {@code ConverterHarness.startConversion(...)}, {@code OptionParser.parse(...)},
 * or {@code CapgenWrapper.generateCAPFile(...)}. It follows the compact path
 * observed from bytecode and calls the underlying typed classes directly.</p>
 */
public final class OracleClassicCapBuilder {
    private OracleClassicCapBuilder() {
    }

    /**
     * Builds one classic CAP file using Oracle internals without CLI entry points.
     *
     * <p>The build sequence is intentionally explicit: construct the converter
     * profile, run compact conversion to JCA/EXP, parse that JCA, link it, serialize
     * a CAP, and finally stage the generated CAP at the resource path expected by
     * CINPO runtime code.</p>
     *
     * @param request project and manifest inputs needed for CAP generation
     * @return paths to both Oracle's generated CAP and CINPO's staged runtime resource
     */
    public static OracleClassicCapResult build(OracleClassicCapRequest request) throws Exception {
        Files.createDirectories(request.workDirectory());
        Files.createDirectories(request.stagedCapFile().getParent());

        AppletManifest manifest = request.manifest();
        Path packageOutputRoot = request.workDirectory().resolve("converter-output");
        Files.createDirectories(packageOutputRoot);

        ConversionProfile profile = CinpoConverterLogic.profileFor(
                manifest,
                request.projectDirectory(),
                request.compiledClassesRoot(),
                packageOutputRoot,
                request.toolLibraryDirectory()
        );

        // This is the bytecode-equivalent compact converter path:
        // new Converter(profile, exportFiles); converter.convert(0, null, 0)
        Converter converter = CinpoConverterLogic.convertCompact(profile);

        // Generated JCA is parsed and linked manually. This replaces the public
        // CapgenWrapper method while preserving its core behavior.
        JCPackage jcPackage = CinpoJcasmLogic.parseGeneratedJca(profile);
        Path generatedCap = CinpoCapgenLogic.generateCap(profile, converter, jcPackage);

        stage(generatedCap, request.stagedCapFile());
        return new OracleClassicCapResult(generatedCap, request.stagedCapFile());
    }

    /**
     * Copies Oracle's package-path CAP output to CINPO's dotted classpath resource.
     *
     * <p>Oracle writes below a slash-separated package output tree, while CINPO
     * runtime installation later loads {@code cap/<dotted-package>.cap}. This copy
     * is the narrow point where those two naming conventions meet.</p>
     */
    private static void stage(Path generatedCap, Path stagedCap) throws IOException {
        if (!Files.isRegularFile(generatedCap)) {
            throw new JavaCardToolException("Generated CAP file not found: " + generatedCap);
        }
        Files.createDirectories(stagedCap.getParent());
        Files.copy(generatedCap, stagedCap, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
