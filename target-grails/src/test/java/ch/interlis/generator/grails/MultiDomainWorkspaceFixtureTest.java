package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDomainWorkspaceFixtureTest {

    @TempDir
    Path tempDir;

    @Test
    void installsExplicitTypedCommandBoundaryAndTechnicalLockSupport() throws IOException {
        Path applicationYaml = tempDir.resolve("grails-app/conf/application.yml");
        Files.createDirectories(applicationYaml.getParent());
        Files.writeString(applicationYaml, "spring:\n  application:\n    name: reference\n", StandardCharsets.UTF_8);
        for (String domain : new String[]{"Parcel", "Building", "Owner"}) {
            Path domainFile = tempDir.resolve("grails-app/domain/com/example/domain/" + domain + ".groovy");
            Files.createDirectories(domainFile.getParent());
            Files.writeString(domainFile,
                "package com.example.domain\n\nclass " + domain + " {\n\n    static mapping = {\n        version false\n    }\n}\n",
                StandardCharsets.UTF_8);
        }

        MultiDomainWorkspaceFixture.install(tempDir);

        String service = Files.readString(tempDir.resolve(
            "grails-app/services/com/example/ParcelWorkspaceCommandService.groovy"), StandardCharsets.UTF_8);
        String controller = Files.readString(tempDir.resolve(
            "grails-app/controllers/com/example/ParcelWorkspaceController.groovy"), StandardCharsets.UTF_8);

        assertThat(service).contains("@Transactional");
        assertThat(service).contains("Parcel.get(command.parcelId)");
        assertThat(service).contains("Building.get(id)");
        assertThat(service).contains("Owner.get(id)");
        assertThat(service).contains("removedBuildingIds");
        assertThat(service).contains("removedOwnerIds");
        assertThat(service).doesNotContain("bindData");
        assertThat(service).doesNotContain("Class.forName");
        assertThat(controller).contains("ParcelWorkspaceCommand command");
        assertThat(controller).doesNotContain("bindData");

        assertThat(Files.readString(tempDir.resolve(
            "grails-app/controllers/com/example/ParcelWorkspaceCommand.groovy"), StandardCharsets.UTF_8))
            .contains("List<BuildingEditCommand>")
            .contains("List<OwnerEditCommand>");
        for (String domain : new String[]{"Parcel", "Building", "Owner"}) {
            assertThat(Files.readString(tempDir.resolve(
                "grails-app/domain/com/example/domain/" + domain + ".groovy"), StandardCharsets.UTF_8))
                .contains("Long version")
                .doesNotContain("version false");
        }
    }
}
