package com.evento.application;

import com.evento.application.scanner.fixtures.AnnotatedComponentFixture;
import com.evento.application.scanner.fixtures.PlainComponentFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsmClassMetadataScannerTest {

    @Test
    void annotatedClassReturnsDescriptionAndDetail() {
        var result = AsmClassMetadataScanner.scan(AnnotatedComponentFixture.class);

        assertThat(result.description()).isEqualTo("Test Component");
        assertThat(result.detail()).isEqualTo("Markdown detail for testing");
    }

    @Test
    void unannotatedClassFallsBackToSimpleName() {
        var result = AsmClassMetadataScanner.scan(PlainComponentFixture.class);

        assertThat(result.description()).isEqualTo("PlainComponentFixture");
        assertThat(result.detail()).isEmpty();
    }

    @Test
    void sourcePathEndsWithExpectedFilename() {
        var result = AsmClassMetadataScanner.scan(AnnotatedComponentFixture.class);

        assertThat(result.sourcePath())
                .endsWith("AnnotatedComponentFixture.java")
                .contains("com/evento/application/scanner/fixtures/");
    }

    @Test
    void declarationLineIsPositive() {
        var result = AsmClassMetadataScanner.scan(AnnotatedComponentFixture.class);

        assertThat(result.declarationLine()).isGreaterThan(0);
    }

    @Test
    void plainClassSourcePathIsPopulated() {
        var result = AsmClassMetadataScanner.scan(PlainComponentFixture.class);

        assertThat(result.sourcePath()).endsWith("PlainComponentFixture.java");
        assertThat(result.declarationLine()).isGreaterThan(0);
    }

    @Test
    void scannerClassItselfReturnsNonEmptyPath() {
        var result = AsmClassMetadataScanner.scan(AsmClassMetadataScanner.class);

        assertThat(result.sourcePath()).endsWith("AsmClassMetadataScanner.java");
    }
}
