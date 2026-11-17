package com.sun.javacard.jcasm.cap;

import com.sun.javacard.converter.ConversionProfile;
import com.sun.javacard.converter.Converter;
import com.sun.javacard.converter.ConverterException;
import com.sun.javacard.converter.util.Names;
import com.sun.javacard.exportfile.ExportFileManager;
import com.sun.javacard.jcasm.Globals;
import com.sun.javacard.jcasm.JCPackage;
import com.sun.javacard.offcardverifier.Verifier;
import com.sun.javacard.offcardverifier.VerifierError;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Vector;

/**
 * Wrapperless CAP archive generation for CINPO.
 *
 * <p>This class intentionally lives in {@code com.sun.javacard.jcasm.cap}. It
 * needs package-local access to the same CAP writer/linker family used by Oracle's
 * own wrapper. The method bodies follow the compact path observed from
 * {@code CapgenWrapper.generateCAPFile(...)} while avoiding that wrapper and any
 * CLI-shaped entry point.</p>
 */
public final class CinpoCapgenLogic {
    private static final String VERIFIER_ERROR_PREFIX = "com.sun.javacard.offcardverifier.VerifierError:";

    private CinpoCapgenLogic() {
    }

    /**
     * Links a parsed JCA package, writes the compact CAP archive, and verifies it.
     *
     * <p>This method is the wrapperless replacement for the compact branch of
     * Oracle's CAP wrapper. It deliberately calls {@link CapFile}, {@link CapLinker},
     * and {@link CapFile#gen(File, ConversionProfile, Converter)} directly so CAP
     * generation remains an in-process typed operation rather than a CLI-style
     * invocation.</p>
     *
     * @param profile converter profile used to produce the JCA and locate output files
     * @param converter converter instance that owns export-file state from the conversion phase
     * @param jcPackage parsed JCA package produced by {@code CinpoJcasmLogic}
     * @return path to Oracle's generated CAP file before CINPO stages it as a resource
     */
    public static Path generateCap(ConversionProfile profile, Converter converter, JCPackage jcPackage)
            throws ConverterException, IOException, CapGenWrapperException {
        String packageName = profile.package_profile.package_name;
        File capFile = new File(profile.getFullOutputPath(), Names.getCAPFileName(packageName));
        CapFile cap = null;
        boolean verifierFailure = false;

        try {
            // CapgenWrapper first materializes a CapFile object from the parsed JCA
            // package, but only if the global JCASM error counter is still clean.
            if (Globals.getErrors() == 0) {
                cap = new CapFile(jcPackage);
            }

            // The linker resolves import/export references between the parsed JCA
            // package and the converter's export-file manager before bytes are
            // serialized into CAP components.
            if (Globals.getErrors() == 0) {
                CapLinker.link(new JCPackage[] { jcPackage }, converter);
            }

            // Static resources are only valid for the old 2.3 package shape in the
            // observed Oracle path. Preserve that guard even though CINPO does not
            // yet expose static resources in manifest.yaml.
            if (profile.staticResourceProfiles != null && !jcPackage.is23Package()) {
                throw new ConverterException("Static resources require a 2.3 package: " + jcPackage.getName());
            }
            if (Globals.getErrors() == 0) {
                // This is the actual archive writer. It produces the package-name
                // derived CAP file inside Oracle's converter output tree; CINPO later
                // copies that file to the dotted classpath resource path.
                cap.gen(capFile, profile, converter);
                verifyIfNeeded(profile, converter, jcPackage, capFile);
            }
        } catch (IOException | ConverterException | VerifierError e) {
            Globals.incrementErrors();
            verifierFailure = e instanceof VerifierError;
            String translated = translate(e, cap, converter);
            if (translated != null) {
                throw new ConverterException(translated);
            }
            throw e;
        }

        if (Globals.getErrors() != 0) {
            // Match Oracle's defensive behavior: a CAP produced during a failed
            // generation/verification pass is not trustworthy and must not be staged.
            deleteBrokenCap(capFile);
            if (verifierFailure && !profile.noVerify) {
                throw new CapGenWrapperException();
            }
            throw new CapGenWrapperException();
        }
        return capFile.toPath();
    }

    /**
     * Runs the same verifier decision tree as the observed Oracle wrapper path.
     *
     * <p>Normalized CAP output and regular CAP output populate verifier inputs in
     * slightly different ways. Keeping this split visible is safer than hiding it in
     * a single generic helper because the export-manager side effects are meaningful
     * to Oracle's verifier.</p>
     */
    private static void verifyIfNeeded(
            ConversionProfile profile,
            Converter converter,
            JCPackage jcPackage,
            File capFile
    ) throws ConverterException, IOException {
        if (profile.noVerify) {
            return;
        }

        boolean normalizedCap = (profile.output & ConversionProfile.OUTPUT_NORMALIZED_CAP)
                == ConversionProfile.OUTPUT_NORMALIZED_CAP;
        if (normalizedCap) {
            if (profile.this_exp_provided) {
                converter.getExportFileManager().load(profile.package_profile.package_name);
            }
            verify(converter, capFile, jcPackage.getName());
            return;
        }

        File expFile = new File(profile.getFullOutputPath(), Names.getExportFileName(profile.package_profile.package_name));
        if (expFile.exists() && Globals.getErrors() == 0) {
            converter.getExportFileManager().buildExportFile(expFile, profile.package_profile.package_name);
            verify(converter, capFile, jcPackage.getName());
        }
    }

    private static void verify(Converter converter, File capFile, String packageName)
            throws ConverterException, IOException {
        ExportFileManager exportFileManager = converter.getExportFileManager();

        // Oracle's private callVerifier forces java/lang to be loaded before it
        // collects standard API export files. Keep that side effect because verifier
        // input ordering and cache population are internal tool assumptions.
        exportFileManager.getExportFile("java/lang");

        HashSet<File> exportFiles = new HashSet<>();
        exportFiles.addAll(exportFileManager.getJavaExportFiles());

        if (converter.getConversion_profile().nobanner) {
            Verifier.setNoBanner();
        }
        Verifier.verifyCap(capFile, packageName, new Vector<>(exportFiles));
    }

    /**
     * Converts low-level Oracle exceptions into the translated message Oracle would
     * normally surface through its wrapper.
     *
     * <p>The translation is best-effort: if Oracle cannot provide an
     * {@link ErrorTranslator}, the original exception is preserved by the caller.</p>
     */
    private static String translate(Exception exception, CapFile cap, Converter converter) {
        if (cap != null) {
            ErrorTranslator translator = ErrorTranslator.getTranslator(
                    exception,
                    cap.getCapGen(),
                    converter.getExportFileManager(),
                    converter
            );
            if (translator != null) {
                return translator.toString();
            }
        }
        String text = exception.toString();
        int verifierIndex = text.indexOf(VERIFIER_ERROR_PREFIX);
        if (verifierIndex >= 0) {
            return text.substring(verifierIndex + VERIFIER_ERROR_PREFIX.length()).trim();
        }
        return null;
    }

    /** Deletes a CAP file produced during a failed generation or verification pass. */
    private static void deleteBrokenCap(File capFile) {
        if (capFile.exists() && !capFile.delete()) {
            capFile.deleteOnExit();
        }
    }
}
