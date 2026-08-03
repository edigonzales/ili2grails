package ch.interlis.generator.grails.verification.report;

import ch.interlis.generator.grails.verification.environment.VerificationEnvironment;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentDetector;
import ch.interlis.generator.grails.verification.environment.VerificationEnvironmentOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Entry point für den Gradle-Task :target-grails:writeVerificationSummary:
 * sammelt die beobachtbaren Verifikationsergebnisse aus den Report- und
 * Test-Ergebnissen und schreibt summary.json/summary.md (Spezifikation §12).
 */
public final class VerificationSummaryTask {

    private VerificationSummaryTask() {
    }

    public static void main(String[] args) throws Exception {
        String reportDir = System.getProperty("reportDir");
        if (reportDir == null) {
            throw new IllegalStateException("System property reportDir is required");
        }
        Path targetDir = Path.of(reportDir);
        Path repositoryRoot = targetDir.getParent().getParent().getParent();
        VerificationEnvironment environment = new VerificationEnvironmentDetector().detect(
            repositoryRoot, VerificationEnvironmentOptions.defaults());

        List<VerificationCheckResult> checks = new ArrayList<>();
        checks.add(checkTargetTests(repositoryRoot, "core"));
        checks.add(checkTargetTests(repositoryRoot, "grails-runtime-api"));
        checks.add(checkTargetTests(repositoryRoot, "grails-runtime"));
        checks.add(checkTargetTests(repositoryRoot, "target-grails"));
        checks.add(checkTargetTests(repositoryRoot, "target-django"));
        checks.add(checkTargetTests(repositoryRoot, "cli"));
        checks.add(checkReports(repositoryRoot, "model-corpus",
            List.of("corpus-results.json"), "corpus"));
        checks.add(checkModuleReports(repositoryRoot, "target-grails",
            "grails-postgres-contract",
            List.of("p0-persistence-contract/mapping-comparison.json",
                "geometry-basic/mapping-comparison.json"), "mapping-contract"));
        checks.add(checkTargetTests(repositoryRoot, "target-grails",
            "grailsRuntimeSmokeTest"));
        checks.add(checkTargetTests(repositoryRoot, "target-grails",
            "realIli2dbSmokeTest"));
        checks.add(checkTargetTests(repositoryRoot, "target-grails",
            "grailsPostgresContractTest"));
        checks.add(checkTargetTests(repositoryRoot, "target-grails",
            "browserE2eTest"));
        checks.add(checkReports(repositoryRoot, "ili2grails-verification",
            List.of(), "summary"));

        VerificationSummary summary = new VerificationSummary(
            1, environment.gitCommit(), environment, checks);
        VerificationSummaryWriter writer = new VerificationSummaryWriter();
        writer.writeJson(summary, targetDir.resolve("summary.json"));
        writer.writeMarkdown(summary, targetDir.resolve("summary.md"));
        System.out.println("writeVerificationSummary: " + (summary.passed() ? "PASSED" : "FAILED")
            + (summary.complete() ? " (complete)" : " (incomplete)"));
        if (!summary.passed()) {
            System.exit(1);
        }
    }

    private static VerificationCheckResult checkTargetTests(Path repositoryRoot, String module) {
        return checkTargetTests(repositoryRoot, module, "test");
    }

    private static VerificationCheckResult checkTargetTests(Path repositoryRoot, String module,
                                                            String testTask) {
        Path results = repositoryRoot.resolve(module).resolve("build/test-results").resolve(testTask);
        String id = module + ":" + testTask;
        if (!Files.isDirectory(results)) {
            return new VerificationCheckResult(id, VerificationStatus.SKIPPED_INFRASTRUCTURE,
                "no test results", List.of(), List.of());
        }
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        try (Stream<Path> files = Files.list(results)) {
            for (Path xml : files.filter(path -> path.getFileName().toString().startsWith("TEST-"))
                .filter(path -> path.getFileName().toString().endsWith(".xml")).toList()) {
                var root = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(xml.toFile());
                tests += Integer.parseInt(root.getDocumentElement().getAttribute("tests"));
                failures += Integer.parseInt(root.getDocumentElement().getAttribute("failures"));
                errors += Integer.parseInt(root.getDocumentElement().getAttribute("errors"));
                skipped += Integer.parseInt(root.getDocumentElement().getAttribute("skipped"));
            }
        } catch (Exception e) {
            return new VerificationCheckResult(id, VerificationStatus.FAILED,
                "could not read test results: " + e.getMessage(), List.of(),
                List.of(e.getMessage()));
        }
        VerificationStatus status = failures + errors == 0
            ? VerificationStatus.PASSED : VerificationStatus.FAILED;
        return new VerificationCheckResult(id, status,
            tests + " tests, " + failures + " failures, " + errors + " errors, "
                + skipped + " skipped",
            List.of(module + "/build/reports/tests/" + testTask + "/index.html"), List.of());
    }

    private static VerificationCheckResult checkReports(Path repositoryRoot, String relative,
                                                        List<String> evidence,
                                                        String id) {
        Path reportDir = repositoryRoot.resolve("build/reports").resolve(relative);
        if (!Files.isDirectory(reportDir)) {
            return new VerificationCheckResult(id, VerificationStatus.SKIPPED_INFRASTRUCTURE,
                "no reports under build/reports/" + relative, List.of(), List.of());
        }
        List<String> missing = evidence.stream()
            .filter(file -> !Files.isRegularFile(reportDir.resolve(file)))
            .toList();
        if (!missing.isEmpty()) {
            return new VerificationCheckResult(id, VerificationStatus.FAILED,
                "missing evidence: " + missing, List.of(), missing);
        }
        return new VerificationCheckResult(id, VerificationStatus.PASSED,
            "reports present under build/reports/" + relative,
            evidence.stream().map(file -> "build/reports/" + relative + "/" + file).toList(),
            List.of());
    }

    private static VerificationCheckResult checkModuleReports(Path repositoryRoot, String module,
                                                              String relative,
                                                              List<String> evidence,
                                                              String id) {
        Path reportDir = repositoryRoot.resolve(module).resolve("build/reports").resolve(relative);
        if (!Files.isDirectory(reportDir)) {
            return new VerificationCheckResult(id, VerificationStatus.SKIPPED_INFRASTRUCTURE,
                "no reports under build/reports/" + relative, List.of(), List.of());
        }
        List<String> missing = evidence.stream()
            .filter(file -> !Files.isRegularFile(reportDir.resolve(file)))
            .toList();
        if (!missing.isEmpty()) {
            return new VerificationCheckResult(id, VerificationStatus.FAILED,
                "missing evidence: " + missing, List.of(), missing);
        }
        return new VerificationCheckResult(id, VerificationStatus.PASSED,
            "reports present under build/reports/" + relative,
            evidence.stream().map(file -> "build/reports/" + relative + "/" + file).toList(),
            List.of());
    }
}
