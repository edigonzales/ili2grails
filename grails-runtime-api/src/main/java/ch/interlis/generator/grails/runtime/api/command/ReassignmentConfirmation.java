package ch.interlis.generator.grails.runtime.api.command;

/**
 * Reassignment confirmation payload for inverse relationship commands.
 */
public record ReassignmentConfirmation(
    String relatedId,
    String relatedLabel,
    String previousOwnerId,
    String previousOwnerLabel,
    String newOwnerId,
    String newOwnerLabel,
    String targetTypeLabel
) {
}
