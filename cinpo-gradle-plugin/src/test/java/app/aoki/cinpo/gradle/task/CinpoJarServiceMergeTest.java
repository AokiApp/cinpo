package app.aoki.cinpo.gradle.task;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the service-file merge performed while building an appliance JAR.
 */
class CinpoJarServiceMergeTest {

    private static final String ENTRY = "META-INF/services/com.example.Service";
    private static final String NAME = "com.example.Service";

    @Test
    void linesPassThroughVerbatimIncludingComments() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                ("# provider list\n"
                        + "com.example.Alpha # default\n"
                        + "\n"
                        + "com.example.Beta\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("# provider list", "com.example.Alpha # default", "", "com.example.Beta"),
                new ArrayList<>(merged.get(NAME)));
    }

    @Test
    void multipleFilesConcatenateInCallOrder() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Beta\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
    }

    @Test
    void duplicateLinesCollapseToFirstOccurrence() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Alpha\ncom.example.Beta\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("com.example.Alpha", "com.example.Beta"), new ArrayList<>(merged.get(NAME)));
    }

    @Test
    void byteOrderMarkStaysPartOfTheLine() {
        // ServiceLoader rejects a BOM-prefixed provider name, so such a jar already
        // fails on a plain classpath. The merge keeps that behavior instead of
        // silently repairing what the JDK refuses.
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "\uFEFFcom.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("\uFEFFcom.example.Alpha"), new ArrayList<>(merged.get(NAME)));
    }
}
