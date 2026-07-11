package ch.interlis.generator.grails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void associationDefaultsAreStable() {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example").build();

        assertThat(config.getAssociationUiMode()).isEqualTo(GenerationConfig.ASSOCIATION_UI_AUTO);
        assertThat(config.getAssociationPageSize()).isEqualTo(10);
        assertThat(config.isHideContextualAssociationControllers()).isTrue();
        assertThat(config.getAssociationNavigation()).isEqualTo(GenerationConfig.ASSOCIATION_NAVIGATION_AUTO);
        assertThat(config.isAssociationUiEnabled()).isTrue();
        assertThat(config.isAssociationUiEditable()).isTrue();
    }

    @Test
    void rejectsInvalidAssociationPageSize() {
        assertThatThrownBy(() -> GenerationConfig.builder(tempDir, "com.example").associationPageSize(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenerationConfig.builder(tempDir, "com.example").associationPageSize(101))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(GenerationConfig.builder(tempDir, "com.example")
            .associationPageSize(100)
            .build()
            .getAssociationPageSize()).isEqualTo(100);
    }

    @Test
    void editableModeEnablesWrites() {
        GenerationConfig config = GenerationConfig.builder(tempDir, "com.example")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_EDITABLE)
            .build();

        assertThat(config.isAssociationUiEnabled()).isTrue();
        assertThat(config.isAssociationUiEditable()).isTrue();
    }

    @Test
    void readOnlyModeDisablesWrites() {
        GenerationConfig readOnly = GenerationConfig.builder(tempDir, "com.example")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_READ_ONLY)
            .build();
        assertThat(readOnly.isAssociationUiEnabled()).isTrue();
        assertThat(readOnly.isAssociationUiEditable()).isFalse();

        GenerationConfig off = GenerationConfig.builder(tempDir, "com.example")
            .associationUiMode(GenerationConfig.ASSOCIATION_UI_OFF)
            .build();
        assertThat(off.isAssociationUiEnabled()).isFalse();
        assertThat(off.isAssociationUiEditable()).isFalse();
    }

    @Test
    void rejectsUnsupportedModes() {
        assertThatThrownBy(() -> GenerationConfig.builder(tempDir, "com.example").associationUiMode("nope"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GenerationConfig.builder(tempDir, "com.example").associationNavigation("nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
