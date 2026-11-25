package dev.evidencerag.api;

import dev.evidencerag.domain.Citation;
import dev.evidencerag.domain.RagAnswer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDtosTest {
    @Test void mapsGroundedAnswerAndItsReferences() {
        var citation = new Citation(UUID.randomUUID(), "contrato-ficticio.pdf", UUID.randomUUID(), 2, .88, "Prazo de cinco dias.");
        var result = ApiDtos.QueryResponse.from(new RagAnswer(UUID.randomUUID(), "GROUNDED", "Resposta", List.of(citation)));
        assertThat(result.status()).isEqualTo("GROUNDED");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().getFirst().pageNumber()).isEqualTo(2);
        assertThat(result.citations().getFirst().excerpt()).isEqualTo("Prazo de cinco dias.");
    }
}
