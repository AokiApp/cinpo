package app.aoki.cinpo.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CinpoLayoutTest {

    @Test
    void capResourcePath_UsesDottedPackageNameUnderCapDirectory() {
        assertEquals("cap/app.aoki.cinpo.example.cap",
                CinpoLayout.capResourcePath("app.aoki.cinpo.example"));
    }

    @Test
    void simulatorResourcePrefix_EndsWithTrailingSlash() {
        assertEquals("cinpo-simulator/linux-amd64/",
                CinpoLayout.simulatorResourcePrefix("linux-amd64"));
    }
}
