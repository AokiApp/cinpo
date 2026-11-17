package app.aoki.cinpo.runtime;

import app.aoki.cinpo.config.BuiltinProfiles;
import app.aoki.cinpo.config.Profile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAdaptersTest {

    @Test
    void registered_ContainsBuiltInRuntimes() {
        assertTrue(RuntimeAdapters.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("jcresim"::equalsIgnoreCase));
        assertTrue(RuntimeAdapters.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("jcardengine"::equalsIgnoreCase));
        assertTrue(RuntimeAdapters.registered().stream()
                .map(RuntimeAdapter::name)
                .anyMatch("pcsc"::equalsIgnoreCase));
    }

    @Test
    void forName_IsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals("pcsc", RuntimeAdapters.forName("  PCSC  ").name());
        assertEquals("jcardengine", RuntimeAdapters.forName("JCARDENGINE").name());
        assertEquals("jcresim", RuntimeAdapters.forName("JcreSim").name());
    }

    @Test
    void forName_UnknownRuntimeFailsClearly() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> RuntimeAdapters.forName("jcardsim"));
        assertTrue(e.getMessage().contains("jcardsim"));
    }

    @Test
    void forProfile_ResolvesRuntime() {
        Profile profile = new Profile("test", "pcsc", BuiltinProfiles.JCDKSIM.secureChannel());
        assertEquals("pcsc", RuntimeAdapters.forProfile(profile).name());
    }
}
