package ch.interlis.generator;

import java.util.Arrays;

enum CliTarget {
    GRAILS("grails"),
    DJANGO("django");

    private final String cliName;

    CliTarget(String cliName) {
        this.cliName = cliName;
    }

    String cliName() {
        return cliName;
    }

    static CliTarget fromCliName(String value) {
        return Arrays.stream(values())
            .filter(target -> target.cliName.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported target: " + value + " (expected grails or django)"
            ));
    }
}
