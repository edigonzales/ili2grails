package ch.interlis.generator.grails.runtime.api.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandResultTest {

    @Test
    void associationCreatedResultMatchesLegacyJsonShape() {
        AssociationCommandResult result = AssociationCommandResult.created(
            "42", "interlis.association.created", "Die Zuordnung wurde erstellt.");
        assertThat(result.success()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.status()).isEqualTo(CommandStatus.SUCCESS);
        assertThat(result.code()).isEqualTo(CommandCode.CREATED);
        assertThat(result.associationId()).isEqualTo("42");
        assertThat(result.fieldErrors()).isEmpty();
        assertThatThrowsUnsupportedMutation(result.fieldErrors());
    }

    @Test
    void associationFailureResultKeepsStatusAndCode() {
        AssociationCommandResult result = AssociationCommandResult.failure(
            409, CommandStatus.CONFLICT, CommandCode.CARDINALITY_MAX_EXCEEDED,
            "Für dieses Objekt ist bereits die maximal zulässige Anzahl Zuordnungen vorhanden.");
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(409);
        assertThat(result.code()).isEqualTo(CommandCode.CARDINALITY_MAX_EXCEEDED);
    }

    @Test
    void inverseReassignmentRequiredCarriesTypedConfirmation() {
        ReassignmentConfirmation confirmation = new ReassignmentConfirmation(
            "7", "Sieben", "3", "Alt", "9", "Neu", "Zieltyp");
        InverseRelationshipCommandResult result =
            InverseRelationshipCommandResult.reassignmentRequired(confirmation);
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(409);
        assertThat(result.code()).isEqualTo(CommandCode.REASSIGNMENT_CONFIRMATION_REQUIRED);
        assertThat(result.reassignmentConfirmation()).isEqualTo(confirmation);
        assertThat(result.relatedId()).isEqualTo("7");
    }

    @Test
    void inverseAssignSuccessCarriesIds() {
        InverseRelationshipCommandResult result = InverseRelationshipCommandResult.success(
            CommandCode.ASSIGNED, "Der Datensatz wurde zugeordnet.", "7", "9");
        assertThat(result.success()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.relatedId()).isEqualTo("7");
        assertThat(result.ownerId()).isEqualTo("9");
        assertThat(result.reassignmentConfirmation()).isNull();
    }

    @Test
    void fieldErrorsAreDefensiveCopies() {
        FieldError error = new FieldError("name", "blank", "Name fehlt");
        AssociationCommandResult result = new AssociationCommandResult(
            false, 422, CommandStatus.VALIDATION_ERROR, CommandCode.VALIDATION_FAILED,
            "interlis.association.validationFailed", "Die Zuordnung konnte nicht gespeichert werden.",
            null, List.of(error));
        assertThat(result.fieldErrors()).containsExactly(error);
        assertThatThrowsUnsupportedMutation(result.fieldErrors());
    }

    private static void assertThatThrowsUnsupportedMutation(List<?> values) {
        try {
            values.add(null);
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
