package ch.interlis.generator.grails.project.plan;

import ch.interlis.generator.grails.project.GrailsProjectFileOwner;
import ch.interlis.generator.grails.project.GrailsProjectFileRule;
import ch.interlis.generator.grails.project.ProjectCustomizationDiagnostic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validiert Ownership-Regeln (Spezifikation §42.3):
 *
 * <ul>
 *   <li>kein Pfad mit mehreren Ownern;</li>
 *   <li>keine Generator-Datei in Plugin-Runtime-Pfaden;</li>
 *   <li>keine Datei ausserhalb des Projektroots;</li>
 *   <li>keine doppelten Pfade;</li>
 *   <li>keine normalisierten Pfadkollisionen.</li>
 * </ul>
 */
public final class GenerationOwnershipValidator {

    public List<GenerationDiagnostic> validate(Collection<PlannedProjectFile> plannedFiles) {
        List<GenerationDiagnostic> diagnostics = new ArrayList<>();
        Map<String, PlannedProjectFile> byNormalizedPath = new HashMap<>();
        for (PlannedProjectFile file : plannedFiles) {
            Path relativePath = file.relativePath();
            if (relativePath == null || relativePath.isAbsolute()) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.TARGET_PATH_OUTSIDE_PROJECT,
                    relativePath,
                    "planned path is not relative: " + relativePath,
                    Map.of()));
                continue;
            }
            boolean traversal = false;
            for (Path part : relativePath) {
                if (part.toString().equals("..")) {
                    traversal = true;
                    break;
                }
            }
            if (traversal) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.TARGET_PATH_OUTSIDE_PROJECT,
                    relativePath,
                    "planned path escapes the project: " + relativePath,
                    Map.of()));
                continue;
            }
            String normalized = normalize(relativePath);
            PlannedProjectFile previous = byNormalizedPath.putIfAbsent(normalized, file);
            if (previous != null && previous.owner() != file.owner()) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.AMBIGUOUS_FILE_OWNERSHIP,
                    relativePath,
                    "path has multiple owners: " + previous.owner() + " and " + file.owner(),
                    Map.of("previousOwner", previous.owner().name(),
                        "currentOwner", file.owner().name())));
            } else if (previous != null) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.AMBIGUOUS_FILE_OWNERSHIP,
                    relativePath,
                    "duplicate planned path: " + relativePath,
                    Map.of()));
            }
            if (file.owner() == GrailsProjectFileOwner.RUNTIME_PLUGIN) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.FORBIDDEN_RUNTIME_PLUGIN_PATH,
                    relativePath,
                    "generator must not write runtime-plugin paths: " + relativePath,
                    Map.of()));
            }
        }
        return diagnostics;
    }

    public List<GenerationDiagnostic> validateLegacyRules(List<GrailsProjectFileRule> rules) {
        List<GenerationDiagnostic> diagnostics = new ArrayList<>();
        Map<String, GrailsProjectFileOwner> ownersByPath = new HashMap<>();
        for (GrailsProjectFileRule rule : rules) {
            String key = normalize(java.nio.file.Path.of(rule.relativePath()));
            GrailsProjectFileOwner previous = ownersByPath.putIfAbsent(key, rule.owner());
            if (previous != null && previous != rule.owner()) {
                diagnostics.add(new GenerationDiagnostic(
                    ProjectCustomizationDiagnostic.Level.ERROR,
                    GenerationDiagnosticCode.AMBIGUOUS_FILE_OWNERSHIP,
                    java.nio.file.Path.of(rule.relativePath()),
                    "legacy ownership rule conflicts: " + previous + " vs " + rule.owner(),
                    Map.of("previousOwner", previous.name(), "currentOwner", rule.owner().name())));
            }
        }
        return diagnostics;
    }

    private static String normalize(Path relativePath) {
        StringBuilder builder = new StringBuilder();
        for (Path part : relativePath) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(part.toString().toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
