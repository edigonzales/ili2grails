package ch.interlis.generator.grails.verification.environment;

import ch.interlis.generator.grails.verification.contract.CommandResult;
import ch.interlis.generator.grails.verification.contract.CommandRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ermittelt die Verfügbarkeit externer Werkzeuge für erweiterte
 * Verification-Tests (Spezifikation §10.1).
 *
 * <p>Keine privaten Pfade in der Ausgabe: {@code resolvedPath} wird für
 * Reports relativ zum Repository-Root beziehungsweise redigiert zurückgegeben.
 */
public final class VerificationEnvironmentDetector {

    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(30);

    public VerificationEnvironment detect(Path repositoryRoot, VerificationEnvironmentOptions options)
        throws IOException, InterruptedException {
        CommandRunner runner = new CommandRunner();
        Map<ExternalTool, ExternalToolStatus> tools = new EnumMap<>(ExternalTool.class);
        tools.put(ExternalTool.JAVA, detectJava(runner));
        tools.put(ExternalTool.GRAILS, detectGrails(runner));
        tools.put(ExternalTool.DOCKER, detectDocker(runner));
        tools.put(ExternalTool.DOCKER_COMPOSE, detectDockerCompose(runner));
        tools.put(ExternalTool.ILI2PG, detectIli2pg(options.ili2pgHome()));
        if (options.inspectBrowser()) {
            tools.put(ExternalTool.PLAYWRIGHT_CHROMIUM, detectPlaywrightChromium());
        } else {
            tools.put(ExternalTool.PLAYWRIGHT_CHROMIUM, new ExternalToolStatus(
                ExternalTool.PLAYWRIGHT_CHROMIUM, ToolAvailability.NOT_CHECKED, null, null,
                "browser inspection not requested"));
        }
        tools.put(ExternalTool.POSTGRESQL, detectPostgres(options.jdbcUrl()));

        return new VerificationEnvironment(
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            resolveGitCommit(repositoryRoot, runner),
            redactJdbcUrl(options.jdbcUrl()),
            tools
        );
    }

    public ExternalToolStatus detectJava(CommandRunner runner) {
        try {
            CommandResult result = runner.run(Path.of("."),
                List.of(System.getProperty("java.home") + "/bin/java", "-version"), TOOL_TIMEOUT);
            if (result.exitCode() != 0) {
                return new ExternalToolStatus(ExternalTool.JAVA, ToolAvailability.MISSING, null, null,
                    "java -version failed");
            }
            return new ExternalToolStatus(ExternalTool.JAVA, ToolAvailability.AVAILABLE,
                firstLine(result.output()), null, null);
        } catch (IOException | InterruptedException e) {
            return new ExternalToolStatus(ExternalTool.JAVA, ToolAvailability.INVALID, null, null,
                e.getMessage());
        }
    }

    public ExternalToolStatus detectGrails(CommandRunner runner) {
        try {
            CommandResult result = runner.run(Path.of("."), List.of("grails", "--version"), TOOL_TIMEOUT);
            if (result.exitCode() != 0) {
                return new ExternalToolStatus(ExternalTool.GRAILS, ToolAvailability.MISSING, null, null,
                    "grails not found in PATH");
            }
            String version = extractVersion(result.output(), Pattern.compile("Grails version:\\s*(\\S+)"));
            return new ExternalToolStatus(ExternalTool.GRAILS, ToolAvailability.AVAILABLE,
                version == null ? firstLine(result.output()) : version, null, null);
        } catch (IOException | InterruptedException e) {
            return new ExternalToolStatus(ExternalTool.GRAILS, ToolAvailability.MISSING, null, null,
                "grails not found in PATH");
        }
    }

    public ExternalToolStatus detectDocker(CommandRunner runner) {
        try {
            CommandResult result = runner.run(Path.of("."), List.of("docker", "--version"), TOOL_TIMEOUT);
            if (result.exitCode() != 0) {
                return new ExternalToolStatus(ExternalTool.DOCKER, ToolAvailability.MISSING, null, null,
                    "docker not found in PATH");
            }
            return new ExternalToolStatus(ExternalTool.DOCKER, ToolAvailability.AVAILABLE,
                firstLine(result.output()), null, null);
        } catch (IOException | InterruptedException e) {
            return new ExternalToolStatus(ExternalTool.DOCKER, ToolAvailability.MISSING, null, null,
                "docker not found in PATH");
        }
    }

    public ExternalToolStatus detectDockerCompose(CommandRunner runner) {
        try {
            CommandResult result = runner.run(Path.of("."), List.of("docker", "compose", "version"), TOOL_TIMEOUT);
            if (result.exitCode() != 0) {
                return new ExternalToolStatus(ExternalTool.DOCKER_COMPOSE, ToolAvailability.MISSING, null, null,
                    "docker compose not available");
            }
            return new ExternalToolStatus(ExternalTool.DOCKER_COMPOSE, ToolAvailability.AVAILABLE,
                firstLine(result.output()), null, null);
        } catch (IOException | InterruptedException e) {
            return new ExternalToolStatus(ExternalTool.DOCKER_COMPOSE, ToolAvailability.MISSING, null, null,
                "docker compose not available");
        }
    }

    public ExternalToolStatus detectIli2pg(Path configuredHome) {
        if (configuredHome == null) {
            return new ExternalToolStatus(ExternalTool.ILI2PG, ToolAvailability.MISSING, null, null,
                "no ili2pg home configured (-Pili2pgHome=... or ILI2PG_HOME)");
        }
        if (!Files.isDirectory(configuredHome)) {
            return new ExternalToolStatus(ExternalTool.ILI2PG, ToolAvailability.MISSING, null, null,
                "configured ili2pg home is not a directory");
        }
        try (var jars = Files.list(configuredHome)) {
            var jar = jars.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().startsWith("ili2pg-"))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .map(p -> p.getFileName().toString())
                .sorted()
                .findFirst()
                .orElse(null);
            if (jar == null || !Files.isDirectory(configuredHome.resolve("libs"))) {
                return new ExternalToolStatus(ExternalTool.ILI2PG, ToolAvailability.INVALID,
                    null, null, "configured ili2pg home lacks ili2pg-*.jar or libs/");
            }
            String version = jar.replaceFirst("^ili2pg-(.*)\\.jar$", "$1");
            return new ExternalToolStatus(ExternalTool.ILI2PG, ToolAvailability.AVAILABLE,
                version, configuredHome.toString(), null);
        } catch (IOException e) {
            return new ExternalToolStatus(ExternalTool.ILI2PG, ToolAvailability.INVALID, null, null,
                e.getMessage());
        }
    }

    public ExternalToolStatus detectPlaywrightChromium() {
        String cacheDir = System.getProperty("user.home") + "/.cache/ms-playwright";
        try {
            if (!Files.isDirectory(Path.of(cacheDir))) {
                return new ExternalToolStatus(ExternalTool.PLAYWRIGHT_CHROMIUM,
                    ToolAvailability.MISSING, null, null, "no playwright browsers in " + cacheDir);
            }
            try (var browsers = Files.list(Path.of(cacheDir))) {
                var chromium = browsers.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("chromium"))
                    .findFirst()
                    .orElse(null);
                if (chromium == null) {
                    return new ExternalToolStatus(ExternalTool.PLAYWRIGHT_CHROMIUM,
                        ToolAvailability.MISSING, null, null, "no chromium browser in " + cacheDir);
                }
                return new ExternalToolStatus(ExternalTool.PLAYWRIGHT_CHROMIUM,
                    ToolAvailability.AVAILABLE, chromium.getFileName().toString(), null, null);
            }
        } catch (IOException e) {
            return new ExternalToolStatus(ExternalTool.PLAYWRIGHT_CHROMIUM,
                ToolAvailability.INVALID, null, null, e.getMessage());
        }
    }

    public ExternalToolStatus detectPostgres(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new ExternalToolStatus(ExternalTool.POSTGRESQL, ToolAvailability.NOT_CHECKED,
                null, null, "no JDBC URL configured");
        }
        return new ExternalToolStatus(ExternalTool.POSTGRESQL, ToolAvailability.NOT_CHECKED,
            null, null, "reachability is checked by the tests, not pre-detected");
    }

    public String resolveGitCommit(Path repositoryRoot, CommandRunner runner) {
        try {
            CommandResult result = runner.run(repositoryRoot, List.of("git", "rev-parse", "HEAD"), TOOL_TIMEOUT);
            if (result.exitCode() == 0) {
                return result.output().trim();
            }
        } catch (IOException | InterruptedException ignored) {
            // no git repository available; commit stays unknown
        }
        return "unknown";
    }

    public String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        return jdbcUrl.replaceAll("(password=)[^&\\s]*", "$1***")
            .replaceAll("(user=)[^&\\s]*", "$1***")
            .replaceAll("([?&])(pwd|passwd)=[^&\\s]*", "$1$2=***");
    }

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        return stripAnsi(output.strip().lines().findFirst().orElse(null));
    }

    private String extractVersion(String output, Pattern pattern) {
        if (output == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(stripAnsi(output));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String stripAnsi(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\u001B\\[[;\\d]*m", "").trim();
    }
}
