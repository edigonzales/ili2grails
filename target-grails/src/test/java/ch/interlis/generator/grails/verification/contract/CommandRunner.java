package ch.interlis.generator.grails.verification.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Testseitiger Prozessausführer mit hartem Timeout. Ersetzt die bisher in den
 * erweiterten Tests duplizierten runCommand-Helfer.
 */
public final class CommandRunner {

    public CommandResult run(Path workingDirectory, List<String> command, Duration timeout)
        throws IOException, InterruptedException {
        long startNanos = System.nanoTime();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean timedOut = !process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (timedOut) {
            process.destroyForcibly();
        }
        String output = readRemainingOutput(process);
        Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
        if (timedOut) {
            return new CommandResult(-1, output, duration, true);
        }
        return new CommandResult(process.exitValue(), output, duration, false);
    }

    private String readRemainingOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(could not read process output: " + e.getMessage() + ")";
        }
    }
}
