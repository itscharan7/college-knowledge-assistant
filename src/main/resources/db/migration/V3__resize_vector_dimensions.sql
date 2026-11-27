-- Remove the old index before changing the vector column.
DROP INDEX IF EXISTS public.spring_ai_vector_index;

-- Resize the empty vector column to the configured dimension.
ALTER TABLE public.vector_store
    ALTER COLUMN embedding TYPE vector(${embedding-dimensions})
    USING embedding::vector(${embedding-dimensions});

-- Recreate the HNSW cosine similarity index.
CREATE INDEX spring_ai_vector_index
    ON public.vector_store
    USING HNSW (embedding vector_cosine_ops);