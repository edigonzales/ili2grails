package ch.interlis.generator.model;

import ch.interlis.generator.model.builder.AttributeMetadataBuilder;
import ch.interlis.generator.model.builder.ClassMetadataBuilder;
import ch.interlis.generator.model.builder.ModelMetadataBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Freeze-Gate der Modell-Pipeline.
 *
 * <pre>
 * ModelMetadataBuilder → Validierung → Freeze → immutable ModelMetadata
 * </pre>
 *
 * <p>Typ-Auflösung (coreType/javaType) geschieht vor dem Freeze über den
 * {@link AttributeTypeResolver}; nach {@code buildValidated} gibt es keine
 * Setter und keine Lazy-Mutation mehr.</p>
 */
public final class ModelMetadataFactory {

    private final AttributeTypeResolver typeResolver;
    private final ModelMetadataValidator validator;

    public ModelMetadataFactory() {
        this(new AttributeTypeResolver(), new ModelMetadataValidator());
    }

    public ModelMetadataFactory(AttributeTypeResolver typeResolver,
                                ModelMetadataValidator validator) {
        this.typeResolver = Objects.requireNonNull(typeResolver, "typeResolver");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ModelBuildResult build(ModelMetadataBuilder builder, ModelBuildPolicy policy) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(policy, "policy");
        resolveTypes(builder);
        List<ModelMetadataDiagnostic> diagnostics = validator.validate(builder);
        if (policy == ModelBuildPolicy.VALIDATE_BLOCKING
            && diagnostics.stream().anyMatch(ModelMetadataDiagnostic::blocking)) {
            return new ModelBuildResult(null, diagnostics);
        }
        return new ModelBuildResult(builder.buildUnchecked(), diagnostics);
    }

    public ModelMetadata buildValidated(ModelMetadataBuilder builder) {
        Objects.requireNonNull(builder, "builder");
        ModelBuildResult result = build(builder, ModelBuildPolicy.VALIDATE_BLOCKING);
        if (result.hasBlockingDiagnostics()) {
            String summary = result.diagnostics().stream()
                .filter(ModelMetadataDiagnostic::blocking)
                .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                .reduce((left, right) -> left + "\n  - " + right)
                .orElse("unknown validation failure");
            throw new ModelMetadataValidationException(
                "Invalid model metadata:\n  - " + summary, result.diagnostics());
        }
        return result.metadata();
    }

    private void resolveTypes(ModelMetadataBuilder builder) {
        for (ClassMetadataBuilder classBuilder : builder.classBuilders().values()) {
            for (AttributeMetadataBuilder attributeBuilder
                : classBuilder.attributeBuilders().values()) {
                resolveAttributeTypes(attributeBuilder);
            }
        }
        for (ch.interlis.generator.model.builder.AssociationMetadataBuilder association
            : builder.associationBuilders().values()) {
            for (AttributeMetadataBuilder attributeBuilder
                : association.attributeBuilders().values()) {
                resolveAttributeTypes(attributeBuilder);
            }
        }
    }

    private void resolveAttributeTypes(AttributeMetadataBuilder attributeBuilder) {
        if (attributeBuilder.coreType() == null || attributeBuilder.javaType() == null) {
            ResolvedAttributeTypes resolved = typeResolver.resolve(attributeBuilder);
            if (attributeBuilder.coreType() == null) {
                attributeBuilder.coreType(resolved.coreType());
            }
            if (attributeBuilder.javaType() == null) {
                attributeBuilder.javaType(resolved.javaType());
            }
        }
    }
}
