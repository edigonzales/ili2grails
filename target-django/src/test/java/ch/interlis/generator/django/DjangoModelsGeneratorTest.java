package ch.interlis.generator.django;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.AssociationMetadata;
import ch.interlis.generator.model.AssociationRoleMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.CoreType;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.testsupport.MetadataTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DjangoModelsGeneratorTest {

    private static final Path SNAPSHOT_ROOT = Path.of("target-django/src/test/resources/django-snapshots");

    @TempDir
    Path tempDir;

    @Test
    void simpleAddressMergedOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedSimpleAddressMetadata();
        Path outputDir = tempDir.resolve("simple-address-merged");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "simple_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("simple-address-merged", config.getModelsFile());
    }

    @Test
    void simpleAddressIli2cOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/SimpleAddressModel.ili"))
            .readMetadata("SimpleAddressModel");
        Path outputDir = tempDir.resolve("simple-address-ili2c");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "simple_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("simple-address-ili2c", config.getModelsFile());
    }

    @Test
    void structureCompositionOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = new Ili2cModelReader(new File("test-models/StructureCompositionCases.ili"))
            .readMetadata("StructureCompositionCases");
        Path outputDir = tempDir.resolve("structure-composition");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "structure_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("structure-composition", config.getModelsFile());
    }

    @Test
    void associationCasesMergedOutputMatchesSnapshot() throws Exception {
        ModelMetadata metadata = MetadataTestFixtures.readMergedAssociationCasesMetadata();
        Path outputDir = tempDir.resolve("association-cases");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "association_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertSnapshot("association-cases", config.getModelsFile());
    }

    @Test
    void scalarFieldMappingPrefersCoreTypeOverJavaTargetHint() throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata classMetadata = new ClassMetadata("TestModel.Topic.Event");
        AttributeMetadata recordedAt = new AttributeMetadata("RecordedAt");
        recordedAt.setCoreType(CoreType.DATE);
        recordedAt.setJavaType("String");
        classMetadata.addAttribute(recordedAt);
        metadata.addClass(classMetadata);
        Path outputDir = tempDir.resolve("core-type-precedence");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "event_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertThat(Files.readString(config.getModelsFile()))
            .contains("recorded_at = models.DateField(null=True, blank=True)");
    }

    @Test
    void decimalFieldMappingUsesCoreConstraints() throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata classMetadata = new ClassMetadata("TestModel.Topic.Invoice");
        AttributeMetadata amount = new AttributeMetadata("Amount");
        amount.setCoreType(CoreType.NUMERIC);
        amount.setJavaType("BigDecimal");
        amount.setPrecision(8);
        amount.setScale(3);
        classMetadata.addAttribute(amount);
        metadata.addClass(classMetadata);
        Path outputDir = tempDir.resolve("core-decimal-constraints");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "invoice_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertThat(Files.readString(config.getModelsFile()))
            .contains("amount = models.DecimalField(max_digits=8, decimal_places=3, null=True, blank=True)");
    }

    @Test
    void associationMetadataCanDriveAssociationRoleFieldsWithoutRelationships() throws Exception {
        ModelMetadata metadata = new ModelMetadata("TestModel");
        ClassMetadata person = persistentClass("TestModel.Topic.Person");
        ClassMetadata address = persistentClass("TestModel.Topic.Address");
        ClassMetadata personAddress = persistentClass("TestModel.Topic.PersonAddress");
        personAddress.setKind(ClassMetadata.ClassKind.ASSOCIATION);
        metadata.addClass(person);
        metadata.addClass(address);
        metadata.addClass(personAddress);

        AssociationMetadata association = new AssociationMetadata(personAddress.getName());
        association.setAssociationClass(personAddress.getName());
        AssociationRoleMetadata personRole = new AssociationRoleMetadata("Person");
        personRole.setTargetClass(person.getName());
        personRole.setMandatory(true);
        association.addRole(personRole);
        AssociationRoleMetadata addressRole = new AssociationRoleMetadata("Address");
        addressRole.setTargetClass(address.getName());
        association.addRole(addressRole);
        metadata.addAssociation(association);

        Path outputDir = tempDir.resolve("association-metadata");
        DjangoGenerationConfig config = DjangoGenerationConfig.builder(outputDir, "assoc_app").build();

        new DjangoModelsGenerator().generate(metadata, config);

        assertThat(Files.readString(config.getModelsFile()))
            .contains("class PersonAddress(models.Model):")
            .contains("person = models.ForeignKey(\"Person\", on_delete=models.PROTECT, related_name=\"+\")")
            .contains("address = models.ForeignKey(\"Address\", on_delete=models.PROTECT, null=True, blank=True, related_name=\"+\")");
    }

    private void assertSnapshot(String snapshotCase, Path actualFile) throws Exception {
        Path expectedFile = SNAPSHOT_ROOT.resolve(snapshotCase)
            .resolve(actualFile.getParent().getFileName())
            .resolve(actualFile.getFileName());
        String actual = normalize(Files.readString(actualFile));
        if (Boolean.getBoolean("updateDjangoSnapshots") || "true".equals(System.getenv("UPDATE_DJANGO_SNAPSHOTS"))) {
            Files.createDirectories(expectedFile.getParent());
            Files.writeString(expectedFile, actual);
        }

        assertThat(expectedFile)
            .as("Snapshot should exist: %s", expectedFile)
            .exists();
        assertThat(actual)
            .as("Snapshot mismatch for %s", expectedFile)
            .isEqualTo(normalize(Files.readString(expectedFile)));
    }

    private String normalize(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private ClassMetadata persistentClass(String name) {
        ClassMetadata classMetadata = new ClassMetadata(name);
        classMetadata.setKind(ClassMetadata.ClassKind.CLASS);
        classMetadata.setTableName(classMetadata.getSimpleName().toLowerCase());
        return classMetadata;
    }
}
