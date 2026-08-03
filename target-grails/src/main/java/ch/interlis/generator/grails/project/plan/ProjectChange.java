package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;

import java.nio.file.Path;

/**
 * Eine entschiedene Projektänderung (Spezifikation §38.4).
 */
public record ProjectChange(
    Path relativePath,
    ProjectChangeType type,
    GrailsProjectFileOwner owner,
    String previousSha256,
    String plannedSha256,
    String reason,
    byte[] plannedContent
) {

    public boolean mutating() {
        return type == ProjectChangeType.CREATE
            || type == ProjectChangeType.UPDATE
            || type == ProjectChangeType.DELETE;
    }

    public boolean blocked() {
        return type == ProjectChangeType.BLOCKED;
    }
}
