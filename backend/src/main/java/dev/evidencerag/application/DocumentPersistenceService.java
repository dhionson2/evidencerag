package dev.evidencerag.application;

import dev.evidencerag.domain.Chunk;
import dev.evidencerag.domain.DocumentRecord;
import dev.evidencerag.infrastructure.DocumentCatalogRepository;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Keeps database catalog writes and precomputed vector persistence atomic without holding a transaction during embedding calls. */
@Service
public class DocumentPersistenceService {
    private final DocumentCatalogRepository catalog;
    private final VectorStore vectorStore;

    public DocumentPersistenceService(DocumentCatalogRepository catalog, VectorStore vectorStore) {
        this.catalog = catalog;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public DocumentRecord persist(UUID documentId, String filename, String mediaType, String sha,
                                  List<Chunk> chunks, List<UUID> vectorIds, List<EmbeddedDocument> vectors) {
        catalog.lockHash(sha);
        var existing = catalog.findBySha256(sha);
        if (existing.isPresent()) return existing.get();
        DocumentRecord saved = catalog.insertDocument(documentId, filename, mediaType, sha, chunks.size(), Instant.now());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            catalog.insertChunkReference(chunk.id(), documentId, vectorIds.get(i), chunk.index(), chunk.pageNumber(),
                    chunk.startOffset(), chunk.endOffset(), chunk.text());
        }
        vectorStore.upsert(vectors);
        return saved;
    }
}
