package app.aoki.cinpo.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskArgumentsTest {

    @Test
    void empty_ReturnsStableEmptyArguments() {
        TaskArguments arguments = TaskArguments.empty();

        assertTrue(arguments.isEmpty());
        assertFalse(arguments.hasFlag("record"));
        assertTrue(arguments.firstValue("record").isEmpty());
        assertEquals(List.of(), arguments.values("record"));
    }

    @Test
    void constructor_DefensivelyCopiesRawArguments() {
        List<String> raw = new ArrayList<>(List.of("--record", "one"));
        TaskArguments arguments = new TaskArguments(raw);
        raw.set(1, "two");

        assertEquals(List.of("--record", "one"), arguments.raw());
        assertThrows(UnsupportedOperationException.class, () -> arguments.raw().add("three"));
    }

    @Test
    void hasFlag_NormalizesOptionName() {
        TaskArguments arguments = new TaskArguments(List.of("--dry-run", "--record", "one"));

        assertTrue(arguments.hasFlag("dry-run"));
        assertTrue(arguments.hasFlag("--dry-run"));
        assertFalse(arguments.hasFlag("missing"));
    }

    @Test
    void values_ReadsRepeatedSeparatedAndInlineValuesInOrder() {
        TaskArguments arguments = new TaskArguments(List.of(
                "--record", "one",
                "--ignored", "x",
                "--record=two",
                "--record", "three"
        ));

        assertEquals(List.of("one", "two", "three"), arguments.values("record"));
        assertEquals("one", arguments.firstValue("--record").orElseThrow());
    }

    @Test
    void values_AllowsEmptyInlineValueBecauseTemplateOwnsSemantics() {
        TaskArguments arguments = new TaskArguments(List.of("--record="));

        assertEquals(List.of(""), arguments.values("record"));
    }

    @Test
    void values_RejectsMissingSeparatedValue() {
        assertThrows(IllegalArgumentException.class, () -> new TaskArguments(List.of("--record")).values("record"));
        assertThrows(IllegalArgumentException.class, () -> new TaskArguments(List.of("--record", "--other")).values("record"));
    }

    @Test
    void optionName_MustNotBeBlank() {
        TaskArguments arguments = TaskArguments.empty();

        assertThrows(IllegalArgumentException.class, () -> arguments.hasFlag("  "));
        assertThrows(NullPointerException.class, () -> arguments.values(null));
    }
}
