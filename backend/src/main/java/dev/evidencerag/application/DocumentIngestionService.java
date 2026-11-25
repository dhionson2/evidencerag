package dev.evidencerag.application;

import dev.evidencerag.domain.Chunk;
import dev.evidencerag.domain.DocumentRecord;
import dev.evidencerag.domain.ExtractedPage;
import dev.evidencerag.infrastructure.AppProperties;
import dev.evidencerag.infrastructure.DocumentCatalogRepository;
import dev.evidencerag.infrastructure.EmbeddingModelIdentity;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentIngestionService {
    private final AppProperties properties;
    private final DocumentChunkingService chunking;
    private final DocumentCatalogRepository catalog;
    private final EmbeddingModel embeddingModel;
    private final DocumentPersistenceService persistence;
    private final MeterRegistry metrics;
    private final EmbeddingModelIdentity embeddingIdentity;

    public DocumentIngestionService(AppProperties properties, DocumentChunkingService chunking,
                                    DocumentCatalogRepository catalog, EmbeddingModel embeddingModel,
                                    DocumentPersistenceService persistence,
                                    MeterRegistry metrics, EmbeddingModelIdentity embeddingIdentity) {
        this.properties = properties;
        this.chunking = chunking;
        this.catalog = catalog;
        this.embeddingModel = embeddingModel;
        this.persistence = persistence;
        this.metrics = metrics;
        this.embeddingIdentity = embeddingIdentity;
    }

    public DocumentRecord ingest(MultipartFile file) {
        long started = System.nanoTime();
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("O arquivo está vazio.");
            if (file.getSize() > properties.maxUploadBytes()) throw new IllegalArgumentException("Arquivo acima do limite permitido.");
            String filename = safeFilename(file.getOriginalFilename());
            String extension = extension(filename);
            if (!extension.equals("pdf") && !extension.equals("md") && !extension.equals("markdown")) {
                throw new IllegalArgumentException("Formato não suportado. Envie PDF ou Markdown.");
            }
            String contentType = file.getContentType();
            if (contentType != null && !contentType.isBlank() && !contentType.equals("application/octet-stream")) {
                boolean pdfType = extension.equals("pdf") && contentType.equalsIgnoreCase("application/pdf");
                boolean markdownType = !extension.equals("pdf") && (contentType.equalsIgnoreCase("text/markdown") || contentType.equalsIgnoreCase("text/plain"));
                if (!pdfType && !markdownType) throw new IllegalArgumentException("Tipo de conteúdo incompatível com a extensão do arquivo.");
            }
            byte[] bytes = file.getBytes();
            if (extension.equals("pdf") && (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F')) {
                throw new IllegalArgumentException("O conteúdo não corresponde a um PDF válido.");
            }
            String sha = sha256(bytes);
            catalog.lockHash(sha);
            var existing = catalog.findBySha256(sha);
            if (existing.isPresent()) return existing.get();
            List<ExtractedPage> pages = extract(extension, bytes);
            int chars = pages.stream().mapToInt(p -> p.text().length()).sum();
            if (chars > properties.maxDocumentCharacters()) throw new IllegalArgumentException("Documento excede o limite de texto extraído.");
            List<Chunk> chunks = chunking.chunk(pages, properties.chunkSize(), properties.chunkOverlap());
            if (chunks.isEmpty()) throw new IllegalArgumentException(extension.equals("pdf")
                    ? "Não foi possível extrair texto deste PDF. Se for um documento escaneado, a demo não possui OCR; envie um PDF com texto selecionável ou Markdown."
                    : "Não foi possível extrair texto indexável do arquivo.");
            UUID documentId = UUID.randomUUID();
            if (catalog.findBySha256(sha).isPresent()) return catalog.findBySha256(sha).orElseThrow();
            List<UUID> vectorIds = chunks.stream().map(ignored -> UUID.randomUUID()).toList();
            List<Document> vectorDocuments = java.util.stream.IntStream.range(0, chunks.size()).mapToObj(i -> {
                Chunk chunk = chunks.get(i);
                UUID vectorId = vectorIds.get(i);
                Map<String, Object> metadata = Map.of("documentId", documentId.toString(),
                        "chunkId", chunk.id().toString(), "filename", filename,
                        "chunkIndex", chunk.index(), "pageNumber", chunk.pageNumber() == null ? 0 : chunk.pageNumber(),
                        "startOffset", chunk.startOffset(), "endOffset", chunk.endOffset(),
                        "visibility", properties.documentVisibility(), "embeddingModel", embeddingIdentity.value());
                return new Document(vectorId.toString(), chunk.text(), metadata);
            }).toList();
            List<float[]> embeddings = embeddingModel.embed(vectorDocuments.stream().map(Document::getText).toList());
            if (embeddings.size() != vectorDocuments.size() || embeddings.stream().anyMatch(v -> v.length != properties.embeddingDimensions())) {
                throw new IllegalStateException("O modelo retornou embeddings incompatíveis com a dimensão configurada.");
            }
            List<EmbeddedDocument> embedded = java.util.stream.IntStream.range(0, vectorDocuments.size())
                    .mapToObj(i -> new EmbeddedDocument(vectorDocuments.get(i), embeddings.get(i))).toList();
            DocumentRecord saved = persistence.persist(documentId, filename,
                    extension.equals("pdf") ? "application/pdf" : "text/markdown", sha, chunks, vectorIds, embedded);
            metrics.counter("evidencerag.ingestion.documents", "result", "success").increment();
            return saved;
        } catch (IOException ex) {
            metrics.counter("evidencerag.ingestion.errors", "stage", "extract").increment();
            throw new IllegalArgumentException("Falha ao ler ou extrair o documento.", ex);
        } catch (RuntimeException ex) {
            metrics.counter("evidencerag.ingestion.errors", "stage", "index").increment();
            throw ex;
        } finally {
            metrics.timer("evidencerag.ingestion.duration").record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private List<ExtractedPage> extract(String extension, byte[] bytes) throws IOException {
        if (!extension.equals("pdf")) return List.of(new ExtractedPage(null, new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > 500) throw new IllegalArgumentException("PDF excede o limite de 500 páginas.");
            PDFTextStripper stripper = new PDFTextStripper();
            java.util.ArrayList<ExtractedPage> pages = new java.util.ArrayList<>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedPage(page, stripper.getText(pdf)));
            }
            return pages;
        }
    }

    private static String safeFilename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome de arquivo obrigatório.");
        String safe = name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (safe.length() > 255) safe = safe.substring(safe.length() - 255);
        return safe;
    }
    private static String extension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
