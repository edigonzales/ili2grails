package ch.interlis.generator.grails.project.plan;

import java.nio.file.Path;

/**
 * Reine Text-Edit-Planung für Updater (Spezifikation §41.5): der Updater
 * berechnet den neuen Inhalt, schreibt aber selbst nichts.
 */
public record TextFileEdit(
    Path relativePath,
    String updatedContent,
    boolean changed,
    String reason
) {
}
