package ch.interlis.generator.grails.verification.contract;

import java.io.IOException;
import java.time.Duration;

/**
 * Ergebnis eines {@link CommandRunner}-Aufrufs.
 */
public record CommandResult(
    int exitCode,
    String output,
    Duration duration,
    boolean timedOut
) {

    public void requireSuccess(String operation) throws IOException {
        if (timedOut) {
            throw new IOException(operation + " timed out after " + duration);
        }
        if (exitCode != 0) {
            throw new IOException(operation + " failed with exit code " + exitCode + ":\n" + output);
        }
    }
}
