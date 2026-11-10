package nl.enadeag.carchargeplanner.start;

import nl.enadeag.carchargeplanner.cli.ExitCode;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.instance.ClaimOutcome;
import nl.enadeag.carchargeplanner.instance.InstanceCommand;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the app does with the outcome of its single-instance claim: whether it runs or ends, with which exit code,
 * what it tells the owner on standard error, and what it logs once the log file is open.
 */
final class ClaimReport {

    private final SingleInstance instance;
    private final Messages messages;
    private final PrintStream err;
    private final System.Logger log;
    private ClaimOutcome outcome;

    /** The log is written to only once {@link #logProblem()} is called. */
    ClaimReport(SingleInstance instance, Messages messages, PrintStream err, System.Logger log) {
        this.instance = instance;
        this.messages = messages;
        this.err = err;
        this.log = log;
    }

    /**
     * Gives empty when this copy runs; otherwise the exit code it ends with: {@link ExitCode#SUCCESS} after handing
     * over to the running copy.
     */
    Optional<ExitCode> claim(Consumer<InstanceCommand> handler) {
        outcome = instance.claim(handler);
        return switch (outcome) {
            case HANDED_OVER -> end(messages.text(TextKey.CLI_HANDED_OVER, AppName.TITLE), ExitCode.SUCCESS);
            case SILENT -> end(messages.text(TextKey.CLI_NO_ANSWER, AppName.TITLE), ExitCode.FAILURE);
            case UNREACHABLE -> end(messages.text(TextKey.CLI_UNREACHABLE, AppName.TITLE), ExitCode.UNREACHABLE);
            case NOT_PRIVATE -> end(messages.text(TextKey.CLI_INSTANCE_NOT_PRIVATE, AppName.TITLE,
                    instance.problem().orElse("")), ExitCode.FAILURE);
            case RUNNING_UNREACHABLE -> {
                err.println(messages.text(TextKey.CLI_RUNS_UNREACHABLE, AppName.TITLE));
                yield Optional.empty();
            }
            case RUNNING, UNAVAILABLE -> Optional.empty(); // the problem is logged once the log file is open
        };
    }

    private Optional<ExitCode> end(String text, ExitCode code) {
        err.println(text);
        return Optional.of(code);
    }

    /**
     * Logs why the single-instance guard does not fully work, if it does not; called once the log file is open, as a
     * copy that ends does not write to it.
     *
     * @throws IllegalStateException before {@link #claim(Consumer)}
     */
    void logProblem() {
        if (outcome == null) {
            throw new IllegalStateException("nothing was claimed yet");
        }
        switch (outcome) {
            case RUNNING_UNREACHABLE -> log.log(Level.WARNING, "a later start cannot reach this copy to show its "
                    + "dashboard, nor can the quit command, but the instance lock still keeps a second copy from "
                    + "running: " + instance.problem().orElse(""));
            case UNAVAILABLE -> log.log(Level.WARNING, "no instance lock, so a second copy of the app could start: "
                    + instance.problem().orElse(""));
            default -> {
                // the guard works
            }
        }
    }
}
