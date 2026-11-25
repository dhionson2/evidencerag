package dev.evidencerag.application;

import dev.evidencerag.domain.Citation;
import dev.evidencerag.infrastructure.AppProperties;
import dev.evidencerag.infrastructure.EmbeddingModelIdentity;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentSearchService {
    private final VectorStore vectorStore;
    private final AppProperties properties;
    private final EmbeddingModelIdentity embeddingIdentity;
    public DocumentSearchService(VectorStore vectorStore, AppProperties properties, EmbeddingModelIdentity embeddingIdentity) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.embeddingIdentity = embeddingIdentity;
    }
    public List<Citation> search(String question) {
        SearchRequest.Builder request = SearchRequest.builder().query(question).topK(properties.retrievalTopK())
                .similarityThreshold(properties.minSimilarity());
        String modelFilter = "(embeddingModel == '" + embeddingIdentity.value() + "' || embeddingModel == '"
                + embeddingIdentity.legacyValue() + "')";
        request.filterExpression(properties.publicDemoCorpusOnly()
                ? "visibility == 'public-demo' && " + modelFilter
                : modelFilter);
        List<Document> hits = vectorStore.similaritySearch(request.build());
        if (hits == null) return List.of();
        return hits.stream().map(this::toCitation).toList();
    }
    private Citation toCitation(Document document) {
        Map<String, Object> m = document.getMetadata();
        return new Citation(UUID.fromString(m.get("documentId").toString()), m.get("filename").toString(),
                UUID.fromString(m.get("chunkId").toString()), page(m.get("pageNumber")),
                document.getScore(), document.getText());
    }
    private Integer page(Object value) {
        if (value == null) return null;
        int page = Integer.parseInt(value.toString());
        return page <= 0 ? null : page;
    }
}
