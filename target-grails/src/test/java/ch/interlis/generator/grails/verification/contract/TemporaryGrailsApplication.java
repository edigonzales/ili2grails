package ch.interlis.generator.grails.verification.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Testseitige temporäre Grails-App: erzeugt die App, installiert die
 * Runtime-Artefakte, kompiliert und führt Integrationstests aus
 * (Spezifikation §30.4).
 */
public final class TemporaryGrailsApplication implements AutoCloseable {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(15);

    private final Path root;
    private final String appName;
    private final Path parentDir;
    private final CommandRunner runner;

    private TemporaryGrailsApplication(Path parentDir, String appName, CommandRunner runner) {
        this.parentDir = parentDir;
        this.appName = appName;
        this.root = parentDir.resolve(appName);
        this.runner = runner;
    }

    public static TemporaryGrailsApplication create(Path parentDir, String appName,
                                                    CommandRunner runner)
        throws IOException, InterruptedException {
        TemporaryGrailsApplication app = new TemporaryGrailsApplication(parentDir, appName, runner);
        CommandResult result = runner.run(parentDir, List.of("grails", "create-app", appName),
            COMMAND_TIMEOUT);
        if (result.exitCode() != 0) {
            throw new IOException("grails create-app failed:\n" + result.output());
        }
        app.root.resolve("gradlew").toFile().setExecutable(true);
        app.root.resolve("grailsw").toFile().setExecutable(true);
        if (!Files.isRegularFile(app.root.resolve("grailsw"))) {
            throw new IOException("grails create-app did not produce " + app.root);
        }
        return app;
    }

    public Path directory() {
        return root;
    }

    public void installRuntimeArtifacts(Path generatedDomainRoot, String sourceSubPath)
        throws IOException {
        // Die generierten Domains liegen bereits im App-Verzeichnis; hier wird
        // nur der Runtime-Plugin-Vertrag geprüft.
        if (!Files.isDirectory(root.resolve("src/main/groovy/ch/interlis/generator/grails/runtime"))
            && !Files.isDirectory(root.resolve("grails-app/services/ch/interlis/generator/grails/runtime"))) {
            throw new IOException(
                "no local runtime copies allowed; runtime must come from the plugin JAR");
        }
    }

    public void compile() throws IOException, InterruptedException {
        runCommand(List.of("./gradlew", "compileGroovy"));
    }

    public CommandResult runIntegrationTests(List<String> testNames)
        throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of(
            "./gradlew", "integrationTest", "--no-daemon"));
        for (String testName : testNames) {
            command.add("--tests");
            command.add(testName);
        }
        return runCommandResult(command);
    }

    public void runCommand(List<String> command) throws IOException, InterruptedException {
        CommandResult result = runCommandResult(command);
        if (result.exitCode() != 0) {
            throw new IOException("command failed (exit " + result.exitCode() + "): "
                + String.join(" ", command) + "\n" + result.output());
        }
    }

    public CommandResult runCommandResult(List<String> command)
        throws IOException, InterruptedException {
        return runner.run(root, command, COMMAND_TIMEOUT);
    }

    @Override
    public void close() {
        // Temporäre Apps bleiben im Build-Verzeichnis für Debug-Artefakte.
    }
}
