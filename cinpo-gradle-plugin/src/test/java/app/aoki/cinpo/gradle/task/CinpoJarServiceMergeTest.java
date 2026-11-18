package app.aoki.cinpo.gradle.task;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for the service-file merge performed while building an appliance JAR.
 */
class CinpoJarServiceMergeTest {

    private static final String ENTRY = "META-INF/services/com.example.Service";
    private static final String NAME = "com.example.Service";

    @Test
    void inlineCommentsAreNotPartOfProviderNames() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                ("com.example.Alpha # 注釈\n"
                        + "com.example.Beta\t# note\n"
                        + "# コメント行\n"
                        + "\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(Set.of("com.example.Alpha", "com.example.Beta"), merged.get(NAME));
    }

    @Test
    void bomIsStrippedFromTheFirstProvider() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "\uFEFFcom.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(Set.of("com.example.Alpha"), merged.get(NAME));
    }

    @Test
    void commentOnlyFileLeavesNoEmptyEntry() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "# only a comment\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(merged.containsKey(NAME));
    }

    @Test
    void duplicateProvidersKeepFirstSeenOrder() {
        LinkedHashMap<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        CinpoJarTask.collectServiceLines(merged, ENTRY,
                "com.example.Beta\ncom.example.Alpha\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("com.example.Alpha", "com.example.Beta"),
                new ArrayList<>(merged.get(NAME)));
    }
}
