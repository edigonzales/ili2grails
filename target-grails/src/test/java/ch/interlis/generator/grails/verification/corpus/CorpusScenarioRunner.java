package ch.interlis.generator.grails.verification.corpus;

import ch.interlis.generator.grails.GeneratedGroovyCompiler;
import ch.interlis.generator.grails.GenerationConfig;
import ch.interlis.generator.grails.GeneratedGroovyCompiler;
import ch.interlis.generator.grails.GrailsCrudGenerator;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.reader.ili2db.Ili2dbReadResult;
import org.opentest4j.TestAbortedException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static ch.interlis.generator.grails.verification.report.VerificationStatus.FAILED;
import static ch.interlis.generator.grails.verification.report.VerificationStatus.PASSED;
import static ch.interlis.generator.grails.verification.report.VerificationStatus.SKIPPED_INFRASTRUCTURE;

/**
 * Führt Corpus-Szenarien aus (Spezifikation §26): semantische Lesung über
 * ili2c, Grails-Generierung und optional den Datenbank-Vertrag.
 */
public final class CorpusScenarioRunner {

    /**
     * Semantische Lesung und Generierung ohne Datenbank.
     */
    public CorpusScenarioResult runSemanticAndGeneration(CorpusScenario scenario,
                                                         CorpusExecutionContext context)
        throws Exception {
        List<ObservedDiagnostic> diagnostics = new ArrayList<>();
        try {
            Path modelPath = resolveModelFile(scenario, context);
            Ili2cModelReader reader = new Ili2cModelReader(
                modelPath.toFile(), scenario.repositories());
            ModelMetadata metadata;
            int selectedModels = 1;
            try {
                Ili2cModelReader.Ili2cReadResult readResult = reader.read(scenario.modelName());
                metadata = readResult.metadata();
                if (readResult.modelSelection() != null) {
                    selectedModels = readResult.modelSelection().includedModelNames().size();
                }
            } catch (ch.interlis.ili2c.Ili2cFailure e) {
                if (looksLikeRepositoryProblem(e)) {
                    throw new TestAbortedException(
                        "SKIPPED_INFRASTRUCTURE external model repositories unavailable for "
                            + scenario.id() + ": " + e.getMessage(), e);
                }
                throw e;
            }
            return runGeneration(scenario, context, metadata, selectedModels, diagnostics);
        } catch (TestAbortedException skip) {
            return new CorpusScenarioResult(scenario.id(), SKIPPED_INFRASTRUCTURE,
                new CorpusObservedCounts(0, 0, 0, 0, 0, 0, 0, 0), diagnostics,
                List.of(), null, List.of());
        }
    }

    /**
     * Datenbank-Vertrag: Das Szenario muss {@code database.required=true}
     * haben; der Aufrufer stellt die importierte Schema-Lesung bereit.
     */
    public CorpusScenarioResult runWithDatabase(CorpusScenario scenario,
                                                CorpusExecutionContext context,
                                                Ili2dbReadResult readResult)
        throws Exception {
        if (scenario.database() == null || !scenario.database().required()) {
            throw new IllegalArgumentException(
                "runWithDatabase requires database.required=true for " + scenario.id());
        }
        List<ObservedDiagnostic> diagnostics = new ArrayList<>();
        readResult.diagnostics().forEach(diagnostic -> diagnostics.add(
            new ObservedDiagnostic("reader", diagnostic.code().name(),
                diagnostic.severity().name(), diagnostic.message(), diagnostic.physicalElement())));
        if (!readResult.isUsable()) {
            return new CorpusScenarioResult(scenario.id(), FAILED,
                new CorpusObservedCounts(0, 0, 0, 0, 0, 0, 0, 0), diagnostics,
                List.of(), null, List.of());
        }
        return runGeneration(scenario, context, readResult.metadata(), 1, diagnostics);
    }

    private CorpusScenarioResult runGeneration(CorpusScenario scenario,
                                               CorpusExecutionContext context,
                                               ModelMetadata metadata,
                                               int selectedModels,
                                               List<ObservedDiagnostic> diagnostics)
        throws Exception {
        CorpusObservedCounts counts = count(metadata, selectedModels);
        Path generatedRoot = context.workDirectory().resolve(scenario.id() + "-generated");
        GenerationConfig config = GenerationConfig.builder(generatedRoot, "com.example")
            .domainPackage("com.example.domain")
            .enumPackage("com.example.enums")
            .build();

        List<String> generatedFiles = List.of();
        String fingerprint = null;
        List<String> evidenceFiles = new ArrayList<>();
        try {
            new GrailsCrudGenerator().generate(metadata, config);
            generatedFiles = relativeFiles(generatedRoot);
            fingerprint = fingerprintFiles(generatedRoot);
            if (scenario.expected().compileGeneratedGrails()) {
                GeneratedGroovyCompiler.compileGeneratedSources(generatedRoot);
            }
        } catch (Exception e) {
            diagnostics.add(new ObservedDiagnostic("generation", "GENERATION_FAILED", "ERROR",
                e.getMessage(), scenario.id()));
            return new CorpusScenarioResult(scenario.id(), FAILED, counts, diagnostics,
                generatedFiles, fingerprint, evidenceFiles);
        }

        long blockingCount = diagnostics.stream()
            .filter(diagnostic -> "FATAL".equals(diagnostic.severity())
                || "ERROR".equals(diagnostic.severity()))
            .count();
        boolean pass = blockingCount == scenario.expected().blockingDiagnostics()
            && countsMatch(scenario, counts);
        return new CorpusScenarioResult(scenario.id(), pass ? PASSED : FAILED, counts,
            diagnostics, generatedFiles, fingerprint, evidenceFiles);
    }

    private boolean countsMatch(CorpusScenario scenario, CorpusObservedCounts counts) {
        CorpusExpectation expected = scenario.expected();
        if (expected.exactClasses() != null && counts.classes() != expected.exactClasses()) {
            return false;
        }
        if (expected.minimumClasses() != null && counts.classes() < expected.minimumClasses()) {
            return false;
        }
        if (expected.exactAssociations() != null && counts.associations() != expected.exactAssociations()) {
            return false;
        }
        return true;
    }

    public static CorpusObservedCounts count(ModelMetadata metadata) {
        return count(metadata, 1);
    }

    public static CorpusObservedCounts count(ModelMetadata metadata, int selectedModels) {
        java.util.Collection<ClassMetadata> classes = metadata.getAllClasses();
        int attributes = classes.stream()
            .mapToInt(clazz -> clazz.getAttributes().size())
            .sum();
        return new CorpusObservedCounts(
            selectedModels,
            classes.size(),
            attributes,
            metadata.getAllRelationships().size(),
            metadata.getAssociations().size(),
            metadata.getEnums().size(),
            0,
            0
        );
    }

    private Path resolveModelFile(CorpusScenario scenario, CorpusExecutionContext context) {
        Path modelPath = scenario.modelFile().isAbsolute()
            ? scenario.modelFile()
            : context.repositoryRoot().resolve(scenario.modelFile());
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException("Model file not found: " + modelPath);
        }
        return modelPath;
    }

    private boolean looksLikeRepositoryProblem(ch.interlis.ili2c.Ili2cFailure failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        return message.contains("unknownhost")
            || message.contains("timed out")
            || message.contains("could not find model")
            || message.contains("failed to get model");
    }

    private static List<String> relativeFiles(Path root) throws java.io.IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .map(root::relativize)
                .map(Path::toString)
                .sorted(Comparator.naturalOrder())
                .toList();
        }
    }

    private static String fingerprintFiles(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String relative : relativeFiles(root)) {
            Path file = root.resolve(relative);
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(file));
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
