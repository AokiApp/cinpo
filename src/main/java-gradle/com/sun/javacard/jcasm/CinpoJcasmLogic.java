package com.sun.javacard.jcasm;

import com.sun.javacard.converter.ConversionProfile;
import com.sun.javacard.converter.util.Names;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * CINPO-owned JCASM parser logic placed in the Oracle JCASM package.
 *
 * <p>The class mirrors the parse portion of {@code CapgenWrapper} but keeps the
 * calls typed and package-local. No reflection is used.</p>
 */
public final class CinpoJcasmLogic {
    private CinpoJcasmLogic() {
    }

    /**
     * Parses the JCA file emitted by the converter into a JCASM package model.
     *
     * <p>This mirrors the parse phase inside Oracle's CAP wrapper without calling
     * that wrapper. The method stays in {@code com.sun.javacard.jcasm} so the parser
     * and package model can be used as typed package-local collaborators rather than
     * via reflection.</p>
     *
     * @param profile populated converter profile whose output directory contains the generated JCA file
     * @return parsed JCASM package ready for linking and CAP serialization
     * @throws IOException if the generated JCA file cannot be read or parsed
     */
    public static JCPackage parseGeneratedJca(ConversionProfile profile) throws IOException {
        // Oracle's wrapper switches JCASM into CAP-generation mode before parsing.
        // Keep that side effect here because later linker/writer code observes the
        // shared Globals state.
        Globals.setMode(1);

        String packageName = profile.package_profile.package_name;
        File jcaFile = new File(profile.getFullOutputPath(), Names.getJcaFileName(packageName));

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(jcaFile))) {
            Parser parser = new Parser(in);

            // The input spec is used by Oracle diagnostics. Without it, parse errors
            // become much less actionable because they lose the generated file path.
            parser.setInputSpec(jcaFile.getAbsolutePath());
            return parser.packageDeclaration();
        } catch (ParseException e) {
            Globals.incrementErrors();
            throw new IOException("Failed to parse generated JCA file: " + jcaFile, e);
        }
    }

    /**
     * Returns the shared JCASM error count used by Oracle parser/linker code.
     *
     * <p>This is exposed for diagnostics only. Production CAP generation treats any
     * non-zero value as a failed build.</p>
     */
    public static int errors() {
        return Globals.getErrors();
    }
}
