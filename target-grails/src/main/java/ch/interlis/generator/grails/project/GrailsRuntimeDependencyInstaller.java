package ch.interlis.generator.grails.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Installs or updates the {@code ili2grails-runtime} plugin dependency in the
 * application build file.
 *
 * <p>Idempotent: an existing dependency is detected and its version updated;
 * no second dependency line is added and no free regex replacement is applied
 * over the whole build file. The managed block markers are documented.</p>
 */
public final class GrailsRuntimeDependencyInstaller {

    private static final String DEPENDENCY_MARKER = "ili2grails-runtime";

    private final ch.interlis.generator.grails.GrailsBuildGradleUpdater buildGradleUpdater =
        new ch.interlis.generator.grails.GrailsBuildGradleUpdater();

    public DependencyUpdateResult install(Path buildFile, RuntimeCoordinates coordinates) throws IOException {
        Objects.requireNonNull(buildFile, "buildFile");
        Objects.requireNonNull(coordinates, "coordinates");

        if (!java.nio.file.Files.exists(buildFile)) {
            return new DependencyUpdateResult(false, coordinates.notation());
        }
        String content = java.nio.file.Files.readString(buildFile);
        boolean wasPresent = content.contains(coordinates.artifact());
        boolean versionMatches = content.contains(coordinates.notation());
        buildGradleUpdater.ensureManagedDependencyBlock(
            buildFile,
            DEPENDENCY_MARKER,
            managedBlock(coordinates)
        );
        return new DependencyUpdateResult(wasPresent && versionMatches, coordinates.notation());
    }

    static String managedBlock(RuntimeCoordinates coordinates) {
        return "// <ili2grails-runtime-dependency>\n"
            + "    implementation \"" + coordinates.notation() + "\"\n"
            + "    // </ili2grails-runtime-dependency>";
    }

    /**
     * Outcome of a dependency install.
     *
     * @param updated version was changed to the requested coordinates
     * @param notation installed coordinates
     */
    public record DependencyUpdateResult(boolean updated, String notation) {
    }
}
