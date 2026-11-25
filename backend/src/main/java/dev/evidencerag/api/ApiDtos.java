package dev.evidencerag.api;

import dev.evidencerag.domain.DocumentRecord;
import dev.evidencerag.domain.RagAnswer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}
    public record QueryRequest(@NotBlank @Size(max = 2000) String question) {}
    public record CitationResponse(UUID documentId, String documentName, UUID chunkId,
                                   Integer pageNumber, Double similarity, String excerpt) {}
    public record QueryResponse(UUID runId, String status, String answer, List<CitationResponse> citations) {
        static QueryResponse from(RagAnswer answer) {
            return new QueryResponse(answer.runId(), answer.status(), answer.answer(), answer.citations().stream()
                    .map(c -> new CitationResponse(c.documentId(), c.documentName(), c.chunkId(), c.pageNumber(), c.similarity(), c.excerpt())).toList());
        }
    }
    public record DocumentResponse(UUID id, String filename, String mediaType, String sha256,
                                   int chunkCount, Instant createdAt) {
        static DocumentResponse from(DocumentRecord d) {
            return new DocumentResponse(d.id(), d.filename(), d.mediaType(), d.sha256(), d.chunkCount(), d.createdAt());
        }
    }
    public record ErrorResponse(String code, String message, String requestId) {}
}
