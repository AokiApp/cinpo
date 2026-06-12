package app.aoki.cinpo.gradle.task;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

public abstract class CinpoRunTask extends DefaultTask {

    private final ConfigurableFileCollection classpath;
    private final Property<String> mainClass;
    private final ListProperty<String> args;
    private final Property<String> argsString;

    public CinpoRunTask() {
        setGroup("cinpo");
        setDescription("Runs the CINPO CLI against this project.");

        ObjectFactory objects = getProject().getObjects();
        classpath = objects.fileCollection();
        mainClass = objects.property(String.class).convention("app.aoki.cinpo.cli.CinpoCli");
        args = objects.listProperty(String.class).convention(List.of("write"));
        argsString = objects.property(String.class);
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Classpath
    public ConfigurableFileCollection getClasspath() {
        return classpath;
    }

    @Input
    public Property<String> getMainClass() {
        return mainClass;
    }

    @Input
    public ListProperty<String> getArgs() {
        return args;
    }

    @Input
    @Optional
    public Property<String> getArgsString() {
        return argsString;
    }

    @org.gradle.api.tasks.options.Option(option = "args", description = "CINPO CLI arguments. The write subcommand is prepended when omitted.")
    public void setArgsString(String argsString) {
        getArgsString().set(argsString);
        getArgs().set(normalizeArgs(tokenizeArgs(argsString)));
    }

    @TaskAction
    public void runTask() {
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(classpath);
            spec.getMainClass().set(getMainClass());
            spec.setArgs(getArgs().get());
            spec.setWorkingDir(getProject().getProjectDir());
        });
    }

    private static List<String> normalizeArgs(List<String> args) {
        if (args.isEmpty()) {
            return List.of("write");
        }
        String first = args.get(0);
        if ("write".equals(first) || "--help".equals(first) || "-h".equals(first) || "--version".equals(first) || "-V".equals(first)) {
            return args;
        }
        List<String> normalized = new ArrayList<>(args.size() + 1);
        normalized.add("write");
        normalized.addAll(args);
        return List.copyOf(normalized);
    }

    private static List<String> tokenizeArgs(String argsString) {
        if (argsString == null || argsString.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaping = false;

        for (int index = 0; index < argsString.length(); index++) {
            char ch = argsString.charAt(index);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\' && !singleQuoted) {
                escaping = true;
                continue;
            }
            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (Character.isWhitespace(ch) && !singleQuoted && !doubleQuoted) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        if (singleQuoted || doubleQuoted) {
            throw new IllegalArgumentException("Unclosed quote in --args: " + argsString);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return List.copyOf(tokens);
    }
}
