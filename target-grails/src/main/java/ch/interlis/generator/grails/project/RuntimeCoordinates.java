package ch.interlis.generator.grails.project;

import ch.interlis.generator.grails.runtime.api.RuntimeVersionContract;

/**
 * Maven coordinates of the runtime plugin consumed by generated applications.
 *
 * <p>The coordinates exist exactly once in the generator; no free coordinate
 * strings in other generator classes.</p>
 */
public record RuntimeCoordinates(
    String group,
    String artifact,
    String version
) {

    public RuntimeCoordinates {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("group must not be blank");
        }
        if (artifact == null || artifact.isBlank()) {
            throw new IllegalArgumentException("artifact must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }

    public static RuntimeCoordinates ili2grailsRuntime() {
        return new RuntimeCoordinates(
            RuntimeVersionContract.RUNTIME_GROUP,
            RuntimeVersionContract.RUNTIME_ARTIFACT,
            RuntimeVersionContract.RUNTIME_VERSION
        );
    }

    public String notation() {
        return group + ":" + artifact + ":" + version;
    }
}
