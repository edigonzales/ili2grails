package ch.interlis.generator.generator;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GeneratedGroovyCompiler {

    private GeneratedGroovyCompiler() {
    }

    static void compileGeneratedSources(Path generatedRoot) throws IOException {
        List<Path> sources = new ArrayList<>();
        sources.addAll(findGroovySources(generatedRoot.resolve("src/main/groovy")));
        sources.addAll(findGroovySources(generatedRoot.resolve("grails-app/domain")));

        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No generated Groovy sources found in " + generatedRoot);
        }

        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setClasspath(System.getProperty("java.class.path"));
        CompilationUnit compilationUnit = new CompilationUnit(configuration);
        for (Path source : sources) {
            compilationUnit.addSource(source.toFile());
        }
        compilationUnit.compile();
    }

    private static List<Path> findGroovySources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".groovy"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }
}
