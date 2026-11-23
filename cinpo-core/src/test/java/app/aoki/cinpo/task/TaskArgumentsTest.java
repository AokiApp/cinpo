package app.aoki.cinpo.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskArgumentsTest {

  /**
   * Creates the shared empty arguments instance and queries its flags and values.
   *
   * <p>This checks that the empty instance reports no flag, no first value and an empty list of
   * values for the queried option name.
   *
   * <p>Benefit: callers have a reliable starting value instead of null or a partially initialized
   * argument set.
   */
  @DisplayName("Empty arguments report no flags and no values.")
  @Test
  void createsEmptyArguments() {
    TaskArguments arguments = TaskArguments.empty();

    assertTrue(arguments.isEmpty());
    assertFalse(arguments.hasFlag("record"));
    assertTrue(arguments.firstValue("record").isEmpty());
    assertEquals(List.of(), arguments.values("record"));
  }

  /**
   * Constructs arguments from a mutable list, changes that list and then inspects the stored
   * arguments.
   *
   * <p>This checks that the constructor takes a snapshot rather than keeping a reference, and that
   * the exposed raw list rejects modification.
   *
   * <p>Benefit: later changes to the caller's list cannot silently change parsed arguments.
   */
  @DisplayName("Constructor copies raw arguments and exposes an unmodifiable list.")
  @Test
  void copiesRawArgumentsDefensively() {
    List<String> raw = new ArrayList<>(List.of("--record", "one"));
    TaskArguments arguments = new TaskArguments(raw);
    raw.set(1, "two");

    assertEquals(List.of("--record", "one"), arguments.raw());
    assertThrows(UnsupportedOperationException.class, () -> arguments.raw().add("three"));
  }

  /**
   * Queries a present option with and without its leading dashes and also queries an absent option.
   *
   * <p>This checks that flag lookup normalizes the option name before comparing, while still
   * distinguishing a flag that is absent.
   *
   * <p>Benefit: callers can use either spelling without changing behavior at the call site.
   */
  @DisplayName("Flag lookup accepts dashed and bare option names.")
  @Test
  void normalizesFlagNames() {
    TaskArguments arguments = new TaskArguments(List.of("--dry-run", "--record", "one"));

    assertTrue(arguments.hasFlag("dry-run"));
    assertTrue(arguments.hasFlag("--dry-run"));
    assertFalse(arguments.hasFlag("missing"));
  }

  /**
   * Collects values from repeated separated options and one inline option, with an unrelated option
   * in between.
   *
   * <p>This checks that both syntaxes contribute to one result list in the original order and that
   * firstValue returns the earliest value.
   *
   * <p>Benefit: templates that consume repeated option values see the order the user typed.
   */
  @DisplayName("Values from separated and inline options keep command-line order.")
  @Test
  void readsValuesInOrder() {
    TaskArguments arguments =
        new TaskArguments(
            List.of("--record", "one", "--ignored", "x", "--record=two", "--record", "three"));

    assertEquals(List.of("one", "two", "three"), arguments.values("record"));
    assertEquals("one", arguments.firstValue("--record").orElseThrow());
  }

  /**
   * Parses an option written with an equals sign and no value, then reads its values.
   *
   * <p>This checks that an empty inline value is kept as an empty string instead of being treated
   * as a missing value or a parse error.
   *
   * <p>Benefit: a template that treats an empty value as meaningful keeps working.
   */
  @DisplayName("An empty inline value is kept as an empty string.")
  @Test
  void allowsEmptyInlineValue() {
    TaskArguments arguments = new TaskArguments(List.of("--record="));

    assertEquals(List.of(""), arguments.values("record"));
  }

  /**
   * Reads values for an option at the end of the argument list and for an option followed by
   * another option.
   *
   * <p>This checks that both cases raise IllegalArgumentException instead of consuming the next
   * option as a value or returning an empty list.
   *
   * <p>Benefit: a typo in command-line input fails immediately instead of producing silently wrong
   * arguments.
   */
  @DisplayName("A separated option without a value is rejected.")
  @Test
  void rejectsMissingValue() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaskArguments(List.of("--record")).values("record"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new TaskArguments(List.of("--record", "--other")).values("record"));
  }

  /**
   * Looks up a blank flag name and reads values with a null option name.
   *
   * <p>This checks that a blank name raises IllegalArgumentException and a null name raises
   * NullPointerException.
   *
   * <p>Benefit: invalid lookups fail at the call site instead of silently matching nothing.
   */
  @DisplayName("Blank and null option names are rejected.")
  @Test
  void rejectsBlankOptionName() {
    TaskArguments arguments = TaskArguments.empty();

    assertThrows(IllegalArgumentException.class, () -> arguments.hasFlag("  "));
    assertThrows(NullPointerException.class, () -> arguments.values(null));
  }
}
