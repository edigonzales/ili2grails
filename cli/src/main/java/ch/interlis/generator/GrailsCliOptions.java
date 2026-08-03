package ch.interlis.generator;

import picocli.CommandLine.Option;

import java.nio.file.Path;

final class GrailsCliOptions {

    private static final String GRAILS_INIT_DEFAULT = "__GRAILS_INIT_DEFAULT__";

    @Option(names = "--grails-output", paramLabel = "<dir>", description = "Output directory for Grails CRUD artifacts.")
    private Path outputDir;

    @Option(
        names = "--grails-init",
        arity = "0..1",
        fallbackValue = GRAILS_INIT_DEFAULT,
        paramLabel = "[appName]",
        description = "Initialize a Grails app in the output directory."
    )
    private String initAppName;

    @Option(
        names = "--grails-package",
        paramLabel = "<package>",
        description = "Base package for generated classes."
    )
    private String basePackage;

    @Option(
        names = "--grails-domain-package",
        paramLabel = "<package>",
        description = "Package for domain classes."
    )
    private String domainPackage;

    @Option(
        names = "--grails-controller-package",
        paramLabel = "<package>",
        description = "Package for controllers."
    )
    private String controllerPackage;

    @Option(names = "--grails-enum-package", paramLabel = "<package>", description = "Package for enums.")
    private String enumPackage;

    @Option(
        names = "--grails-ui-theme",
        paramLabel = "<default|bootstrap>",
        description = "UI theme for scaffold templates."
    )
    private String uiTheme;

    @Option(
        names = "--grails-map-editor",
        paramLabel = "<none|openlayers>",
        description = "Map editor mode."
    )
    private String mapEditor;

    @Option(
        names = "--grails-language",
        paramLabel = "<de-CH|en>",
        description = "Language for the generated Bootstrap UI (default: de-CH)."
    )
    private String language;

    @Option(
        names = "--grails-default-srid",
        paramLabel = "<int>",
        description = "Default SRID for geometry binding/config."
    )
    private Integer defaultSrid;

    @Option(
        names = "--grails-generate-all",
        description = "Run ./grailsw generate-all for each domain. Requires --grails-init."
    )
    private boolean generateAll;

    @Option(
        names = "--grails-association-ui",
        paramLabel = "<auto|off|read-only|editable>",
        description = "Association UX mode for generated Grails artifacts."
    )
    private String associationUi;

    @Option(
        names = "--grails-association-page-size",
        paramLabel = "<1..100>",
        description = "Page size for association related lists (default 10)."
    )
    private Integer associationPageSize;

    @Option(
        names = "--grails-association-navigation",
        paramLabel = "<auto|show|hide>",
        description = "Navigation visibility for association controllers."
    )
    private String associationNavigation;

    @Option(
        names = "--grails-dry-run",
        description = "Plan the generation and write only explicitly requested " +
            "plan reports; the Grails project is not modified. Exit code is " +
            "non-zero when the plan has blockers."
    )
    private boolean dryRun;

    @Option(
        names = "--grails-plan-json",
        paramLabel = "<file>",
        description = "Write the generation plan as JSON to the given file."
    )
    private Path planJson;

    @Option(
        names = "--grails-plan-markdown",
        paramLabel = "<file>",
        description = "Write the generation plan as Markdown to the given file."
    )
    private Path planMarkdown;

    boolean isConfigured() {
        return outputDir != null
            || initAppName != null
            || basePackage != null
            || domainPackage != null
            || controllerPackage != null
            || enumPackage != null
            || uiTheme != null
            || mapEditor != null
            || language != null
            || defaultSrid != null
            || generateAll
            || associationUi != null
            || associationPageSize != null
            || associationNavigation != null
            || dryRun
            || planJson != null
            || planMarkdown != null;
    }

    boolean dryRun() {
        return dryRun;
    }

    Path planJson() {
        return planJson;
    }

    Path planMarkdown() {
        return planMarkdown;
    }

    Path outputDir() {
        return outputDir;
    }

    boolean initRequested() {
        return initAppName != null;
    }

    String initAppName() {
        if (GRAILS_INIT_DEFAULT.equals(initAppName)) {
            return null;
        }
        return initAppName;
    }

    String basePackage() {
        return basePackage;
    }

    String domainPackage() {
        return domainPackage;
    }

    String controllerPackage() {
        return controllerPackage;
    }

    String enumPackage() {
        return enumPackage;
    }

    String uiTheme() {
        return uiTheme;
    }

    String mapEditor() {
        return mapEditor;
    }

    String language() {
        return language;
    }

    Integer defaultSrid() {
        return defaultSrid;
    }

    boolean generateAll() {
        return generateAll;
    }

    String associationUi() {
        return associationUi;
    }

    Integer associationPageSize() {
        return associationPageSize;
    }

    String associationNavigation() {
        return associationNavigation;
    }
}
