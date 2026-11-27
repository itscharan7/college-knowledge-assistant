# Database

PostgreSQL 16 + pgvector. Schema is owned by Flyway (`ddl-auto: validate`).

## Migrations

- `V1__core_schema.sql`: relational tables
- `V2__vector_store.sql`: `vector` extension + `public.vector_store` (HNSW, cosine). The dimension is the Flyway
  placeholder `embedding-dimensions` (env `EMBEDDING_DIMENSIONS`, default 1536). **Fixed once applied.**

## Tables

| Table | Purpose | Notes |
|---|---|---|
| `users` | accounts | unique index on `lower(email)`; `role` = STUDENT/FACULTY/ADMIN; `active` |
| `documents` | PDF metadata | unique `checksum`; `audience`, `category`, `processing_status`, `processing_error`, `version`, `page_count`, `chunk_count` |
| `document_processing_logs` | processing trail | FK to documents, ON DELETE CASCADE |
| `conversations` | one per chat | FK to users, ON DELETE CASCADE |
| `messages` | USER/ASSISTANT turns | `answerable`, `top_score`, `latency_ms` on assistant rows |
| `message_sources` | citations | `document_id` nullable (SET NULL on delete) + stored `document_title`, so history survives deletion |
| `feedback` | ratings | `rating` in (-1, 1); unique `(message_id, user_id)` |
| `unanswered_questions` | admin gap list | separate so admins never read private chats |
| `vector_store` | chunks + embeddings | Spring AI `PgVectorStore` table |

## Vector metadata (per chunk, JSON)

`documentId`, `chunkId`, `chunkIndex`, `documentTitle`, `originalFilename`, `category`, `audience`, `pageNumber`,
`documentVersion`. There is no foreign key from vectors to documents, so deletion goes through
`VectorIndexService.deleteByDocument` (covered by the integration test). Title/category/audience edits are patched into
the metadata directly (no re-embedding).
