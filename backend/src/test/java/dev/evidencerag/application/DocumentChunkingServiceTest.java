package dev.evidencerag.application;

import dev.evidencerag.domain.ExtractedPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentChunkingServiceTest {
    private final DocumentChunkingService service = new DocumentChunkingService();
    @Test void createsOverlappingChunksAndPreservesPage() {
        var chunks = service.chunk(List.of(new ExtractedPage(3, "alpha beta gamma delta epsilon zeta")), 18, 5);
        assertThat(chunks).hasSizeGreaterThan(1).allSatisfy(chunk -> assertThat(chunk.pageNumber()).isEqualTo(3));
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(1).text()).contains("gamma");
    }
    @Test void rejectsOverlapThatConsumesChunk() {
        assertThatThrownBy(() -> service.chunk(List.of(new ExtractedPage(null, "text")), 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void skipsEmptyPages() {
        assertThat(service.chunk(List.of(new ExtractedPage(1, "  \n ")), 20, 2)).isEmpty();
    }
    @Test void keepsMarkdownHeadingWithParagraphAndTracksSourceOffsets() {
        var chunks = service.chunk(List.of(new ExtractedPage(2, "# Reembolso\n\nPrazo de cinco dias úteis.")), 100, 10);
        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.text()).contains("# Reembolso", "Prazo de cinco dias úteis");
            assertThat(chunk.pageNumber()).isEqualTo(2);
            assertThat(chunk.startOffset()).isEqualTo(13);
            assertThat(chunk.endOffset()).isEqualTo(39);
        });
    }
}
