package app.aoki.cinpo.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Runtime loader for CINPO host-side tasks indexed under {@code META-INF/cinpo/<kind>}. */
public final class CardTaskLoader {

  private static final String INDEX_PREFIX = "META-INF/cinpo/";

  private CardTaskLoader() {}

  /**
   * Loads every task of {@code kind}, populates its dependency fields, and sorts by annotation
   * order.
   */
  public static List<CardTask> loadAll(String kind, TaskContext ctx) {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(ctx);

    List<String> fqcns = readIndex(kind);
    if (fqcns.isEmpty()) {
      return List.of();
    }

    List<CardTask> tasks = new ArrayList<>(fqcns.size());
    for (String fqcn : fqcns) {
      CardTask task = instantiate(fqcn, kind);
      verifyKind(task, kind);
      bind(task, ctx);
      tasks.add(task);
    }

    tasks.sort(
        Comparator.comparingInt(
                (CardTask task) -> task.getClass().getAnnotation(CardTaskDef.class).order())
            .thenComparing(task -> task.getClass().getName()));
    return Collections.unmodifiableList(tasks);
  }

  private static List<String> readIndex(String kind) {
    String resourceName = INDEX_PREFIX + kind;
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = CardTaskLoader.class.getClassLoader();
    }

    Set<String> fqcns = new LinkedHashSet<>();
    try {
      Enumeration<URL> resources = loader.getResources(resourceName);
      while (resources.hasMoreElements()) {
        try (InputStream in = resources.nextElement().openStream()) {
          readIndexLines(in, fqcns);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read CINPO task index: " + resourceName, e);
    }
    return List.copyOf(fqcns);
  }

  private static void readIndexLines(InputStream in, Set<String> fqcns) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          fqcns.add(trimmed);
        }
      }
    }
  }

  private static CardTask instantiate(String fqcn, String kind) {
    try {
      Object instance = Class.forName(fqcn).getDeclaredConstructor().newInstance();
      if (!(instance instanceof CardTask task)) {
        throw new IllegalStateException(
            "Indexed @CardTaskDef(\"" + kind + "\") class does not extend CardTask: " + fqcn);
      }
      return task;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to instantiate @CardTaskDef(\""
              + kind
              + "\") CardTask: "
              + fqcn
              + ". The class must have a public no-arg constructor and extend CardTask.",
          e);
    }
  }

  private static void verifyKind(CardTask task, String kind) {
    CardTaskDef annotation = task.getClass().getAnnotation(CardTaskDef.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "Indexed CardTask is missing @CardTaskDef: " + task.getClass().getName());
    }
    if (!kind.equals(annotation.value())) {
      throw new IllegalStateException(
          "Indexed CardTask kind mismatch for "
              + task.getClass().getName()
              + ": index kind='"
              + kind
              + "', annotation kind='"
              + annotation.value()
              + "'");
    }
  }

  private static void bind(CardTask task, TaskContext ctx) {
    task.channel = ctx.channel();
    task.manifest = ctx.manifest();
    task.profile = ctx.profile();
    task.taskArguments = ctx.taskArguments();
    task.secureChannelProfile = ctx.toSecureChannelProfile();
  }
}
