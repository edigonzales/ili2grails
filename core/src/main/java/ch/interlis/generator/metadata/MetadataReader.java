package ch.interlis.generator.metadata;

import ch.interlis.generator.metadata.merge.MergeDiagnostic;
import ch.interlis.generator.metadata.merge.MetadataMergePolicy;
import ch.interlis.generator.metadata.merge.MetadataMergeResult;
import ch.interlis.generator.metadata.merge.MetadataMerger;
import ch.interlis.generator.metadata.selection.ModelSelection;
import ch.interlis.generator.model.ModelMetadata;
import ch.interlis.generator.reader.Ili2cModelReader;
import ch.interlis.generator.reader.Ili2dbMetadataReader;
import ch.interlis.ili2c.Ili2cFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Orchestriert den Metadaten-Lesepfad: ili2c-Auswahl und semantischer Snapshot,
 * physischer Snapshot aus ili2db, danach deterministischer {@link MetadataMerger}.
 *
 * <p>Strategie (mit ili2c):</p>
 * <ol>
 *   <li>Modell einmal kompilieren.</li>
 *   <li>Semantischen Snapshot lesen.</li>
 *   <li>{@link ModelSelection} bestimmen.</li>
 *   <li>Physischen Snapshot nur für die Auswahl lesen.</li>
 *   <li>{@link MetadataMerger#merge} ausführen.</li>
 *   <li>Policy anwenden.</li>
 *   <li>Resultat zurückgeben.</li>
 * </ol>
 *
 * <p>Ohne ili2c gilt {@link ModelSelection#rootOnly(String)} als Fallback.</p>
 */
public class MetadataReader {

    private static final Logger logger = LoggerFactory.getLogger(MetadataReader.class);

    private final Connection connection;
    private final File modelFile;
    private final String schemaName;
    private final List<String> modelDirs;

    public MetadataReader(Connection connection, File modelFile) {
        this(connection, modelFile, null, null);
    }

    public MetadataReader(Connection connection, File modelFile, String schemaName,
                          List<String> modelDirs) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.modelFile = modelFile;
        this.schemaName = schemaName;
        this.modelDirs = modelDirs;
    }

    /**
     * Liest vollständige Metadaten für ein Modell im STRICT-Modus.
     * Blockierende Merge-Diagnostics führen zu einer {@link
     * ch.interlis.generator.metadata.merge.MetadataMergeException}.
     */
    public ModelMetadata readMetadata(String modelName) throws SQLException, Ili2cFailure {
        return readMetadataResult(modelName, MetadataMergePolicy.STRICT).metadata();
    }

    /**
     * Liest vollständige Metadaten inklusive Diagnostics und Modellauswahl.
     */
    public MetadataReadResult readMetadataResult(String modelName, MetadataMergePolicy mergePolicy)
            throws SQLException, Ili2cFailure {
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(mergePolicy, "mergePolicy");
        logger.info("Reading combined metadata for model: {}", modelName);

        boolean hasModelFile = modelFile != null && modelFile.exists();
        boolean hasModelRepositories = modelDirs != null && !modelDirs.isEmpty();

        if (hasModelFile || hasModelRepositories) {
            // 1-3. Modell einmal kompilieren, semantischen Snapshot und Auswahl lesen
            Ili2cModelReader ili2cReader = new Ili2cModelReader(modelFile, modelDirs);
            Ili2cModelReader.Ili2cReadResult ili2cResult = ili2cReader.read(modelName);

            // 4. physischen Snapshot nur für die Modellauswahl lesen
            logger.info("Reading ili2db metadata from database for selection: {}",
                ili2cResult.modelSelection().includedModelNames());
            Ili2dbMetadataReader ili2dbReader = Ili2dbMetadataReader.create(connection, schemaName);
            ModelMetadata physical = ili2dbReader.readMetadata(ili2cResult.modelSelection());

            // 5. Merge
            logger.info("Merging physical and semantic metadata");
            MetadataMergeResult mergeResult = MetadataMerger.defaultMerger()
                .merge(physical, ili2cResult.metadata());

            // 6. Policy anwenden
            if (mergePolicy == MetadataMergePolicy.STRICT) {
                mergeResult.throwIfBlocking();
            }

            logger.info("Metadata reading complete with {} diagnostic(s)",
                mergeResult.diagnostics().size());
            return new MetadataReadResult(
                mergeResult.metadata(),
                mergeResult.diagnostics(),
                ili2cResult.modelSelection()
            );
        }

        // DB-only-Fallback: Root-Modell ohne ili2c-Abhängigkeitsgraph
        logger.warn("No model file or repositories provided. Skipping ili2c enrichment.");
        ModelSelection selection = ModelSelection.rootOnly(modelName);
        Ili2dbMetadataReader ili2dbReader = Ili2dbMetadataReader.create(connection, schemaName);
        ModelMetadata metadata = ili2dbReader.readMetadata(selection);

        List<MergeDiagnostic> validatorDiagnostics =
            new MetadataValidator().validate(metadata);
        MetadataMergeResult result = new MetadataMergeResult(metadata, validatorDiagnostics);
        if (mergePolicy == MetadataMergePolicy.STRICT) {
            result.throwIfBlocking();
        }
        return new MetadataReadResult(metadata, result.diagnostics(), selection);
    }
}
