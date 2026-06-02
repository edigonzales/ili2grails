package ch.interlis.generator;

import picocli.CommandLine.Option;

import java.nio.file.Path;

final class DjangoCliOptions {

    @Option(names = "--django-output", paramLabel = "<dir>", description = "Output directory for Django artifacts.")
    private Path outputDir;

    @Option(names = "--django-app", paramLabel = "<python_package>", description = "Django app package name.")
    private String appName;

    boolean isConfigured() {
        return outputDir != null || appName != null;
    }

    Path outputDir() {
        return outputDir;
    }

    String appName() {
        return appName;
    }
}
