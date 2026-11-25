CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid PRIMARY KEY,
    content text,
    metadata json,
    embedding vector
);

CREATE TABLE IF NOT EXISTS rag_document (
    id uuid PRIMARY KEY,
    filename varchar(255) NOT NULL,
    media_type varchar(100) NOT NULL,
    sha256 char(64) NOT NULL UNIQUE,
    status varchar(20) NOT NULL CHECK (status IN ('INDEXED')),
    chunk_count integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS rag_document_chunk (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    vector_id uuid NOT NULL UNIQUE,
    chunk_index integer NOT NULL,
    page_number integer,
    excerpt text NOT NULL
);
CREATE INDEX IF NOT EXISTS rag_document_chunk_document_idx ON rag_document_chunk(document_id);
