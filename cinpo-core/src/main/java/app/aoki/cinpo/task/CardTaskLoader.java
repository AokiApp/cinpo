package app.aoki.cinpo.task;

import app.aoki.cinpo.apdu.ApduChannel;
import app.aoki.cinpo.config.AppletManifest;
import app.aoki.cinpo.config.Profile;
import app.aoki.cinpo.gp.scp.SecureChannelProfile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime loader for CINPO host-side tasks indexed under {@code META-INF/cinpo/<kind>}.
 */
public final class CardTaskLoader {

    private static final String INDEX_PREFIX = "META-INF/cinpo/";

    private CardTaskLoader() {
    }

    /**
     * Loads every task of {@code kind}, injects declared dependencies, and sorts by annotation order.
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
            inject(task, ctx);
            tasks.add(task);
        }

        tasks.sort(Comparator
                .comparingInt((CardTask task) -> task.getClass().getAnnotation(CardTaskDef.class).order())
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
            Enumeration<java.net.URL> resources = loader.getResources(resourceName);
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
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
                throw new IllegalStateException("Indexed @CardTaskDef(\"" + kind + "\") class does not implement CardTask: " + fqcn);
            }
            return task;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate @CardTaskDef(\"" + kind + "\") CardTask: " + fqcn
                            + ". The class must have a public no-arg constructor and implement CardTask.",
                    e);
        }
    }

    private static void verifyKind(CardTask task, String kind) {
        CardTaskDef annotation = task.getClass().getAnnotation(CardTaskDef.class);
        if (annotation == null) {
            throw new IllegalStateException("Indexed CardTask is missing @CardTaskDef: " + task.getClass().getName());
        }
        if (!kind.equals(annotation.value())) {
            throw new IllegalStateException("Indexed CardTask kind mismatch for " + task.getClass().getName()
                    + ": index kind='" + kind + "', annotation kind='" + annotation.value() + "'");
        }
    }

    private static void inject(Object task, TaskContext ctx) {
        Class<?> type = task.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                Object value = resolve(field.getType(), ctx);
                try {
                    field.setAccessible(true);
                    field.set(task, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Failed to inject field " + field + " on " + task.getClass().getName(), e);
                }
            }
            type = type.getSuperclass();
        }
    }

    private static Object resolve(Class<?> type, TaskContext ctx) {
        if (type == ApduChannel.class) {
            return ctx.channel();
        }
        if (type == AppletManifest.class) {
            return ctx.manifest();
        }
        if (type == Profile.class) {
            return ctx.profile();
        }
        if (type == SecureChannelProfile.class) {
            return ctx.toSecureChannelProfile();
        }
        if (type == TaskArguments.class) {
            return ctx.taskArguments();
        }
        throw new IllegalStateException("Unsupported @Inject field type: " + type.getName()
                + ". Supported types: ApduChannel, AppletManifest, Profile, SecureChannelProfile, TaskArguments");
    }
}
