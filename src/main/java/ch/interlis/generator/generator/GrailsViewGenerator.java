package ch.interlis.generator.generator;

import ch.interlis.generator.model.AttributeMetadata;
import ch.interlis.generator.model.ClassMetadata;
import ch.interlis.generator.model.EnumMetadata;
import ch.interlis.generator.model.ModelMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generiert einfache GSP-Views für CRUD-Oberflächen.
 */
public class GrailsViewGenerator {

    public void generate(ModelMetadata metadata, GenerationConfig config) throws IOException {
        for (ClassMetadata classMetadata : metadata.getAllClasses()) {
            if (classMetadata.isAbstract()) {
                continue;
            }
            Path baseDir = config.getOutputDir()
                .resolve("grails-app/views")
                .resolve(NameUtils.toLowerCamel(classMetadata.getSimpleName()));
            Files.createDirectories(baseDir);
            writeView(baseDir.resolve("list.gsp"), renderList(classMetadata));
            writeView(baseDir.resolve("show.gsp"), renderShow(classMetadata));
            writeView(baseDir.resolve("create.gsp"), renderForm(classMetadata, metadata, "create"));
            writeView(baseDir.resolve("edit.gsp"), renderForm(classMetadata, metadata, "edit"));
        }
    }

    private void writeView(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String renderList(ClassMetadata classMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html>\n<head>\n    <meta name=\"layout\" content=\"main\"/>\n")
            .append("    <title>").append(classMetadata.getSimpleName()).append("</title>\n</head>\n<body>\n");
        sb.append("<h1>").append(classMetadata.getSimpleName()).append("</h1>\n");
        sb.append("<table>\n    <thead>\n        <tr>\n");
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            sb.append("            <th>").append(attr.getName()).append("</th>\n");
        }
        sb.append("        </tr>\n    </thead>\n    <tbody>\n");
        sb.append("    <g:each in=\"${")
            .append(NameUtils.toLowerCamel(classMetadata.getSimpleName()))
            .append("List}\" var=\"item\">\n");
        sb.append("        <tr>\n");
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            sb.append("            <td>${item.")
                .append(NameUtils.toLowerCamel(attr.getName())).append("}</td>\n");
        }
        sb.append("        </tr>\n");
        sb.append("    </g:each>\n");
        sb.append("    </tbody>\n</table>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String renderShow(ClassMetadata classMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html>\n<head>\n    <meta name=\"layout\" content=\"main\"/>\n")
            .append("    <title>").append(classMetadata.getSimpleName()).append("</title>\n</head>\n<body>\n");
        sb.append("<h1>").append(classMetadata.getSimpleName()).append("</h1>\n");
        sb.append("<dl>\n");
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            String propertyName = NameUtils.toLowerCamel(attr.getName());
            sb.append("    <dt>").append(attr.getName()).append("</dt>\n");
            sb.append("    <dd>${").append(propertyName).append("}</dd>\n");
        }
        sb.append("</dl>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String renderForm(ClassMetadata classMetadata, ModelMetadata metadata, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html>\n<head>\n    <meta name=\"layout\" content=\"main\"/>\n")
            .append("    <title>").append(classMetadata.getSimpleName()).append("</title>\n</head>\n<body>\n");
        sb.append("<h1>").append(classMetadata.getSimpleName()).append("</h1>\n");
        sb.append("<g:form action=\"").append(action).append("\">\n");
        for (AttributeMetadata attr : classMetadata.getAllAttributes()) {
            if (attr.isPrimaryKey()) {
                continue;
            }
            sb.append(renderField(attr, metadata));
        }
        sb.append("    <button type=\"submit\">Save</button>\n</g:form>\n</body>\n</html>\n");
        return sb.toString();
    }

    private String renderField(AttributeMetadata attr, ModelMetadata metadata) {
        String name = NameUtils.toLowerCamel(attr.getName());
        StringBuilder sb = new StringBuilder();
        sb.append("    <div class=\"fieldcontain\">\n")
            .append("        <label for=\"").append(name).append("\">")
            .append(attr.getName()).append("</label>\n");

        if (attr.getEnumType() != null) {
            EnumMetadata enumMetadata = metadata.getEnums().get(attr.getEnumType());
            if (enumMetadata != null) {
                sb.append("        <g:select name=\"").append(name)
                    .append("\" from=\"").append(enumMetadata.getSimpleName())
                    .append(".values()\"/>\n");
                sb.append("    </div>\n");
                return sb.toString();
            }
        }

        String javaType = attr.getJavaType();
        if ("Boolean".equals(NameUtils.simpleType(javaType))) {
            sb.append("        <g:checkBox name=\"").append(name).append("\"/>\n");
        } else if ("LocalDate".equals(NameUtils.simpleType(javaType))) {
            sb.append("        <g:datePicker name=\"").append(name).append("\" precision=\"day\"/>\n");
        } else if ("LocalDateTime".equals(NameUtils.simpleType(javaType))) {
            sb.append("        <g:datePicker name=\"").append(name).append("\" precision=\"minute\"/>\n");
        } else {
            sb.append("        <g:textField name=\"").append(name).append("\"/>\n");
        }
        sb.append("    </div>\n");
        return sb.toString();
    }
}
