-- V1 is intentionally immutable: some installations may already have applied it.
-- The placeholder is set to the exact embedding model output dimension at deployment.
ALTER TABLE vector_store
    ALTER COLUMN embedding TYPE vector(${embedding_dimensions})
    USING embedding::vector(${embedding_dimensions});

CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
