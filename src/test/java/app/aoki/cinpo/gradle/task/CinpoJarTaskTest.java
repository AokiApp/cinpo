package app.aoki.cinpo.gradle.task;

import org.junit.jupiter.api.Test;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinpoJarTaskTest {

    private static final String TARGET_API = "3.0.4";

    @Test
    void shouldSkipEntry_ExcludesModuleDescriptorsFromArchiveRootAndMultiReleaseSections() {
        assertTrue(CinpoJarTask.shouldSkipEntry("module-info.class", TARGET_API));
        assertTrue(CinpoJarTask.shouldSkipEntry("META-INF/versions/9/module-info.class", TARGET_API));
        assertTrue(CinpoJarTask.shouldSkipEntry("META-INF/versions/11/module-info.class", TARGET_API));
        assertTrue(CinpoJarTask.shouldSkipEntry("META-INF/versions/25/module-info.class", TARGET_API));
    }

    @Test
    void shouldSkipEntry_ExcludesJcardEngineJavaStubs() {
        assertTrue(CinpoJarTask.shouldSkipEntry("java/lang/Object.class", TARGET_API));
        assertTrue(CinpoJarTask.shouldSkipEntry("java/io/IOException.class", TARGET_API));
        assertTrue(CinpoJarTask.shouldSkipEntry("java/rmi/RemoteException.class", TARGET_API));
    }

    @Test
    void shouldSkipEntry_KeepsOrdinaryClassesResourcesAndVersionedMetadata() {
        assertFalse(CinpoJarTask.shouldSkipEntry("app/aoki/cinpo/cli/CinpoCli.class", TARGET_API));
        assertFalse(CinpoJarTask.shouldSkipEntry("javacard/framework/Applet.class", TARGET_API));
        assertFalse(CinpoJarTask.shouldSkipEntry("com/oracle/smartcardio/socket/SocketCardTerminalProvider.class", TARGET_API));
        assertFalse(CinpoJarTask.shouldSkipEntry("META-INF/versions/9/OSGI-INF/MANIFEST.MF", TARGET_API));
        // Non-numeric release directories are not part of the multi-release spec and stay untouched.
        assertFalse(CinpoJarTask.shouldSkipEntry("META-INF/versions/SNAPSHOT/module-info.class", TARGET_API));
    }

    @Test
    void manifest_DeclaresMainClassAndStableAutomaticModuleName() {
        Manifest manifest = CinpoJarTask.manifest();
        Attributes attributes = manifest.getMainAttributes();

        assertEquals("app.aoki.cinpo.cli.CinpoCli", attributes.getValue(Attributes.Name.MAIN_CLASS));
        assertEquals("app.aoki.cinpo", attributes.getValue(new Attributes.Name("Automatic-Module-Name")));
    }
}
