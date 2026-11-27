# Architecture

Modular monolith, one Spring Boot application (`com.collegeassistant`).

| Package | Responsibility |
|---|---|
| `config` | `AppProperties` (all `app.*` settings), async executor |
| `security` | Spring Security, `AppUserDetails`, admin bootstrap |
| `user` | users, roles, registration, admin user management, `/api/me` |
| `document` | document entity/lifecycle, validation, audience `AccessPolicy`, document + admin-document APIs |
| `storage` | `FileStorageService` (local disk implementation) |
| `ingestion` | `PdfExtractor`, `TextCleaner`, `TableFormatter`, `Chunker`, async `IngestionService` |
| `embedding` | `VectorIndexService`: the only code that writes/deletes vectors |
| `retrieval` | `RetrievalService`: filtered similarity search |
| `rag` | `PromptFactory`, `AnswerParser`, `RagService` |
| `chat` | conversations, messages, sources, unanswered log, chat API |
| `feedback` | ratings |
| `admin` | dashboard stats, unanswered list, feedback list |
| `common` | `ApiException`, uniform error JSON, global handler |
| `web` | Thymeleaf page routes |

## Ingestion workflow

```
Admin upload ─► validate (ext, MIME, %PDF-, PDFBox opens, page cap) ─► SHA-256 duplicate check
  ─► store file ─► row status UPLOADED ─► 202 Accepted
      └─ async (2 threads): PROCESSING
         extract per page ─► clean + tables ─► chunk (per page) ─► delete old vectors
         ─► embed + store in batches (retry x3) ─► READY
         any failure ─► delete partial vectors ─► FAILED + readable error + log
```

On startup, documents stuck in PROCESSING (crash/restart) are marked FAILED so the admin can retry.

## Question-answering workflow

```
question ─► length/quota/ownership checks
  ─► follow-up? rewrite to standalone question (1 extra LLM call, only if history exists)
  ─► embed query ─► similarity search (topK, threshold, audience filter by role)
  ─► re-check each hit in DB (exists, READY, role may view)
  ─► nothing left? ─► "not found" (no LLM call)
  ─► prompt: rules + numbered context blocks [1]..[n] + question ─► LLM (JSON)
  ─► parse; require answerable=true and at least one source number in 1..n
  ─► backend maps numbers ─► (document, page, excerpt) citations
  ─► persist question + answer + sources (+ unanswered log if refused)
```

## AI provider architecture

Business code imports only Spring AI interfaces: `ChatModel`, `EmbeddingModel` (through `VectorStore`), `VectorStore`.
The default provider is Spring AI's OpenAI starter, pointed at any OpenAI-compatible endpoint via `AI_BASE_URL`,
`AI_CHAT_PATH`, `AI_EMBEDDINGS_PATH`, `AI_CHAT_MODEL`, `AI_EMBEDDING_MODEL`. Switching to another officially supported
Spring AI provider means changing the starter dependency and `application.yml`. Changing the **embedding** model also
needs a new `vector_store` migration and re-indexing, because vectors from different models are not comparable.

## Security design

- Passwords hashed with BCrypt; session login; CSRF enabled (JS sends the token from `<meta>` tags).
- Public registration cannot choose a role (there is no role field); admin accounts are only bootstrapped from env.
- `/admin/**`, `/api/admin/**`, Swagger require ADMIN (URL rule + `@PreAuthorize`).
- Document access: audience filter inside the vector query, DB re-check, and role check on the PDF endpoint; denied = 404.
- Conversations: always queried by `(id, userId)`; others get 404. Admins cannot read private chats.
- Uploads: validated, stored under generated UUID names, path-traversal guarded.
- Prompt injection: context is declared untrusted data, the model has no tools, output shape and source numbers are
  validated; only admins can upload documents.
- UI writes user/LLM text with `textContent` (no `innerHTML`), so answers cannot inject markup.
- Secrets only via environment variables; error responses never include stack traces.
