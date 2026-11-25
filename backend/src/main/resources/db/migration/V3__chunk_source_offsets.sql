ALTER TABLE rag_document_chunk
    ADD COLUMN IF NOT EXISTS start_offset integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS end_offset integer NOT NULL DEFAULT 0;
