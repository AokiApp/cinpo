package app.aoki.cinpo.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI entry point for CINPO framework.
 * Used in production mode (fat JAR) and development mode (Gradle JavaExec).
 */
@Command(
    name = "cinpo",
    description = "CINPO - Card Installation and Provision Orchestrator (use --debug for verbose logging)",
    subcommands = {WriteCommand.class},
    mixinStandardHelpOptions = true,
    version = "CINPO 0.1.0"
)
public class CinpoCli {
 
    private static final String DEBUG_OPTION = "--debug";
    private static final String JUL_CONFIG_CLASS_PROPERTY = "java.util.logging.config.class";
    private static final String JUL_CONFIG_FILE_PROPERTY = "java.util.logging.config.file";
 
    private final List<String> taskArguments;
    private final boolean debugEnabled;

    public CinpoCli() {
        this(List.of(), false);
    }

    private CinpoCli(List<String> taskArguments, boolean debugEnabled) {
        this.taskArguments = List.copyOf(taskArguments);
        this.debugEnabled = debugEnabled;
    }

    public List<String> taskArguments() {
        return taskArguments;
    }

    public boolean debugEnabled() {
        return debugEnabled;
    }

    public static void main(String[] args) {
        SplitArguments split = splitArguments(args);
        LoggingSelection loggingSelection = extractLoggingSelection(split.cinpoArgs());
        configureLogging(loggingSelection.debugEnabled());

        CommandLine commandLine = new CommandLine(new CinpoCli(split.taskArguments(), loggingSelection.debugEnabled()));
        if (loggingSelection.debugEnabled()) {
            commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                ex.printStackTrace(cmd.getErr());
                cmd.getErr().flush();
                return cmd.getCommandSpec().exitCodeOnExecutionException();
            });
        }

        int exitCode = commandLine.execute(loggingSelection.cinpoArgs().toArray(String[]::new));
        System.exit(exitCode);
    }

    private static SplitArguments splitArguments(String[] args) {
        List<String> cinpoArgs = new ArrayList<>(args.length);
        List<String> taskArguments = new ArrayList<>();
        boolean forwardToTasks = false;

        for (String arg : args) {
            if (forwardToTasks) {
                taskArguments.add(arg);
                continue;
            }
            if ("--".equals(arg)) {
                forwardToTasks = true;
                continue;
            }
            cinpoArgs.add(arg);
        }

        return new SplitArguments(cinpoArgs, taskArguments);
    }
 
    private static LoggingSelection extractLoggingSelection(List<String> cinpoArgs) {
        boolean debugEnabled = false;
        List<String> sanitizedArgs = new ArrayList<>(cinpoArgs.size());
 
        for (String arg : cinpoArgs) {
            if (DEBUG_OPTION.equals(arg)) {
                debugEnabled = true;
                continue;
            }
            if (arg.startsWith(DEBUG_OPTION + "=")) {
                debugEnabled = parseDebugValue(arg.substring((DEBUG_OPTION + "=").length()));
                continue;
            }
            sanitizedArgs.add(arg);
        }
 
        return new LoggingSelection(sanitizedArgs, debugEnabled);
    }
 
    private static boolean parseDebugValue(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("--debug must be true or false when a value is provided");
    }
 
    private static void configureLogging(boolean debugEnabled) {
        if (hasExternalLoggingConfiguration()) {
            return;
        }
 
        Level threshold = debugEnabled ? Level.FINE : Level.WARNING;
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        rootLogger.setLevel(threshold);
 
        Handler[] handlers = rootLogger.getHandlers();
        if (handlers.length == 0) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(threshold);
            rootLogger.addHandler(handler);
            return;
        }
 
        for (Handler handler : handlers) {
            handler.setLevel(threshold);
        }
    }
 
    private static boolean hasExternalLoggingConfiguration() {
        return System.getProperty(JUL_CONFIG_CLASS_PROPERTY) != null
                || System.getProperty(JUL_CONFIG_FILE_PROPERTY) != null;
    }
 
    private record SplitArguments(List<String> cinpoArgs, List<String> taskArguments) {
        private SplitArguments {
            cinpoArgs = List.copyOf(cinpoArgs);
            taskArguments = List.copyOf(taskArguments);
        }
    }
 
    private record LoggingSelection(List<String> cinpoArgs, boolean debugEnabled) {
        private LoggingSelection {
            cinpoArgs = List.copyOf(cinpoArgs);
        }
    }
}
