package dev.evidencerag.infrastructure;

import dev.evidencerag.domain.DocumentRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentCatalogRepository {
    private final JdbcTemplate jdbc;
    public DocumentCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockHash(String sha) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, sha);
    }
    public Optional<DocumentRecord> findBySha256(String sha) {
        return jdbc.query("select id, filename, media_type, sha256, chunk_count, created_at from rag_document where sha256 = ?",
                (rs, row) -> new DocumentRecord(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getString("media_type"), rs.getString("sha256"), rs.getInt("chunk_count"),
                        rs.getTimestamp("created_at").toInstant()), sha).stream().findFirst();
    }
    public DocumentRecord insertDocument(UUID id, String filename, String mediaType, String sha, int chunks, Instant createdAt) {
        jdbc.update("insert into rag_document(id, filename, media_type, sha256, status, chunk_count, created_at) values (?, ?, ?, ?, 'INDEXED', ?, ?)",
                id, filename, mediaType, sha, chunks, Timestamp.from(createdAt));
        return new DocumentRecord(id, filename, mediaType, sha, chunks, createdAt);
    }
    public void insertChunkReference(UUID chunkId, UUID documentId, UUID vectorId, int index, Integer page,
                                     int startOffset, int endOffset, String excerpt) {
        jdbc.update("insert into rag_document_chunk(id, document_id, vector_id, chunk_index, page_number, start_offset, end_offset, excerpt) values (?, ?, ?, ?, ?, ?, ?, ?)",
                chunkId, documentId, vectorId, index, page, startOffset, endOffset, excerpt);
    }
    public List<DocumentRecord> list() {
        return jdbc.query("select id, filename, media_type, sha256, chunk_count, created_at from rag_document order by created_at desc",
                (rs, row) -> new DocumentRecord(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getString("media_type"), rs.getString("sha256"), rs.getInt("chunk_count"),
                        rs.getTimestamp("created_at").toInstant()));
    }
    public Optional<DocumentRecord> findById(UUID id) {
        return jdbc.query("select id, filename, media_type, sha256, chunk_count, created_at from rag_document where id = ?",
                (rs, row) -> new DocumentRecord(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getString("media_type"), rs.getString("sha256"), rs.getInt("chunk_count"),
                        rs.getTimestamp("created_at").toInstant()), id).stream().findFirst();
    }
    public List<String> vectorIds(UUID id) {
        return jdbc.query("select vector_id::text from rag_document_chunk where document_id = ?", (rs, row) -> rs.getString(1), id);
    }
    public void delete(UUID id) { jdbc.update("delete from rag_document where id = ?", id); }
}
