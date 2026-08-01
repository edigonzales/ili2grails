package ch.interlis.generator.grails.verification.environment;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationEnvironmentDetectorTest {

    @Test
    void redactJdbcUrlRemovesCredentials() {
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        String redacted = detector.redactJdbcUrl(
            "jdbc:postgresql://localhost:54321/edit?user=postgres&password=secret&dbSchema=sa");
        assertThat(redacted)
            .doesNotContain("secret")
            .doesNotContain("user=postgres")
            .contains("password=***")
            .contains("user=***")
            .contains("dbSchema=sa");
    }

    @Test
    void redactJdbcUrlHandlesNullAndBlank() {
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        assertThat(detector.redactJdbcUrl(null)).isNull();
        assertThat(detector.redactJdbcUrl("   ")).isNull();
    }

    @Test
    void detectIli2pgReportsMissingWhenNoHomeConfigured() {
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        ExternalToolStatus status = detector.detectIli2pg(null);
        assertThat(status.availability()).isEqualTo(ToolAvailability.MISSING);
        assertThat(status.diagnostic()).contains("ILI2PG_HOME");
    }

    @Test
    void detectIli2pgReportsInvalidForEmptyDirectory() throws Exception {
        Path tempDir = java.nio.file.Files.createTempDirectory("ili2pg-invalid");
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        ExternalToolStatus status = detector.detectIli2pg(tempDir);
        assertThat(status.availability()).isEqualTo(ToolAvailability.INVALID);
    }

    @Test
    void detectPlaywrightChromiumReportsMissingWhenNotInstalled() {
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        ExternalToolStatus status = detector.detectPlaywrightChromium();
        assertThat(status.availability()).isIn(ToolAvailability.MISSING, ToolAvailability.AVAILABLE);
        if (status.availability() == ToolAvailability.MISSING) {
            assertThat(status.diagnostic()).contains("ms-playwright");
        }
    }

    @Test
    void environmentCopiesToolsMap() {
        VerificationEnvironment environment = new VerificationEnvironment(
            "17", "os", "arch", "commit", "redacted",
            Map.of(ExternalTool.JAVA,
                new ExternalToolStatus(ExternalTool.JAVA, ToolAvailability.AVAILABLE, "17", null, null)));
        assertThat(environment.tool(ExternalTool.JAVA).availability()).isEqualTo(ToolAvailability.AVAILABLE);
        assertThat(environment.tool(ExternalTool.GRAILS).availability()).isEqualTo(ToolAvailability.NOT_CHECKED);
    }

    @Test
    void requireToolSkipsWithMarkerWhenNotRequired() {
        ExternalToolStatus missing = new ExternalToolStatus(
            ExternalTool.GRAILS, ToolAvailability.MISSING, null, null, "grails not found");
        assertThatThrownBy(() -> InfrastructureSupport.requireTool(missing, false, "some test"))
            .isInstanceOf(org.opentest4j.TestAbortedException.class)
            .hasMessageContaining("SKIPPED_INFRASTRUCTURE");
    }

    @Test
    void requireToolFailsWithMarkerWhenRequired() {
        ExternalToolStatus missing = new ExternalToolStatus(
            ExternalTool.GRAILS, ToolAvailability.MISSING, null, null, "grails not found");
        assertThatThrownBy(() -> InfrastructureSupport.requireTool(missing, true, "some test"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("FAILED_INFRASTRUCTURE");
    }

    @Test
    void requireToolAcceptsAvailableTool() {
        ExternalToolStatus available = new ExternalToolStatus(
            ExternalTool.GRAILS, ToolAvailability.AVAILABLE, "7.0.6", null, null);
        assertThat(InfrastructureSupport.requireTool(available, true, "some test"))
            .isSameAs(available);
    }

    @Test
    void detectIli2pgAcceptsConfiguredHome() throws Exception {
        Path tempDir = java.nio.file.Files.createTempDirectory("ili2pg-valid");
        java.nio.file.Files.createDirectories(tempDir.resolve("libs"));
        java.nio.file.Files.writeString(tempDir.resolve("ili2pg-5.5.1.jar"), "jar");
        VerificationEnvironmentDetector detector = new VerificationEnvironmentDetector();
        ExternalToolStatus status = detector.detectIli2pg(tempDir);
        assertThat(status.availability()).isEqualTo(ToolAvailability.AVAILABLE);
        assertThat(status.version()).isEqualTo("5.5.1");
        assertThat(status.resolvedPath()).isEqualTo(tempDir.toString());
    }
}
