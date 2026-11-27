-- Vector table used by Spring AI's PgVectorStore (initialize-schema=false, so Flyway owns it).
-- The dimension comes from the Flyway placeholder 'embedding-dimensions' (env EMBEDDING_DIMENSIONS).
-- It is FIXED once this migration has run. Changing embedding model/dimension later requires a new
-- migration that recreates the table, followed by re-indexing every document.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store (
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(${embedding-dimensions})
);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON public.vector_store USING HNSW (embedding vector_cosine_ops);
