package dev.evidencerag.api;

import dev.evidencerag.application.DocumentIngestionService;
import dev.evidencerag.infrastructure.DocumentCatalogRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.UUID;

@RestController
@Profile("!demo")
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentIngestionService ingestion;
    private final DocumentCatalogRepository catalog;
    private final VectorStore vectorStore;
    public DocumentController(DocumentIngestionService ingestion, DocumentCatalogRepository catalog, VectorStore vectorStore) {
        this.ingestion = ingestion; this.catalog = catalog; this.vectorStore = vectorStore;
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.DocumentResponse upload(@RequestPart("file") MultipartFile file) {
        return ApiDtos.DocumentResponse.from(ingestion.ingest(file));
    }
    @GetMapping
    public List<ApiDtos.DocumentResponse> list() { return catalog.list().stream().map(ApiDtos.DocumentResponse::from).toList(); }
    @DeleteMapping("/{id}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (catalog.findById(id).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado.");
        List<String> vectorIds = catalog.vectorIds(id);
        if (!vectorIds.isEmpty()) vectorStore.delete(vectorIds);
        catalog.delete(id);
    }
}
