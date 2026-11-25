package dev.evidencerag.application;

import dev.evidencerag.domain.Citation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationValidationServiceTest {
    private final CitationValidationService service = new CitationValidationService();
    private final UUID known = UUID.randomUUID();
    private final Citation citation = new Citation(UUID.randomUUID(), "politica.md", known, null, 0.91, "Trecho literal.");
    @Test void returnsOnlyCitationsPresentInRetrievedEvidence() {
        assertThat(service.citedOnly("Prazo de dez dias [chunk:" + known + "]", List.of(citation))).containsExactly(citation);
    }
    @Test void rejectsInventedChunkIds() {
        assertThat(service.citedOnly("Resposta [chunk:" + UUID.randomUUID() + "]", List.of(citation))).isEmpty();
    }
    @Test void answerWithoutCitationCannotBeGrounded() {
        assertThat(service.citedOnly("Resposta genérica.", List.of(citation))).isEmpty();
    }
    @Test void resolvesOnlyIdentifiersRetrievedByThisQuery() {
        var allowed = new Citation(UUID.randomUUID(), "ficcao.md", UUID.randomUUID(), null, 0.9, "trecho fictício");
        var unrelated = new Citation(UUID.randomUUID(), "outro.md", UUID.randomUUID(), null, 0.8, "outro trecho");
        assertThat(service.retrievedOnly(List.of(allowed.chunkId().toString()), List.of(allowed, unrelated)))
                .containsExactly(allowed);
        assertThat(service.retrievedOnly(List.of(UUID.randomUUID().toString()), List.of(allowed))).isEmpty();
        assertThat(service.retrievedOnly(List.of("not-a-uuid"), List.of(allowed))).isEmpty();
    }
}
