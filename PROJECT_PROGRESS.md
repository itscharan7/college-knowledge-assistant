# PROJECT_PROGRESS

_Last updated: 2026-11-26_

## Overall status

**All code is written but NOT yet compiled, run or tested end-to-end.** The build sandbox could not reach Maven Central
(HTTP 403 from the egress proxy; not allowed on the Free plan) and has no Docker daemon, so `mvn`, Docker Compose, Flyway,
PGVector and the AI provider were never exercised. Nothing below is marked "verified" unless it says how.

## What was actually verified

| Check | How | Result |
|---|---|---|
| JavaScript syntax (5 files) | `node --check` | Passed |
| Java parse-level check (66 main files) | `javac -proc:none`, ignoring missing-dependency errors | No syntax errors |
| Chunker, TableFormatter, TextCleaner logic | Isolated copies (Spring annotations stripped) run with a throwaway `Main` | 20/20 checks passed |
| Everything else (Spring wiring, JPA mappings vs. SQL, Flyway, Spring AI calls, security, UI in a browser) | not run | **Unverified** |

## Implemented (written, unverified unless stated above)

- [x] Maven project, Spring Boot 3.5.7, Spring AI 1.1.8 BOM pinned, Java 17
- [x] Flyway V1 (relational schema), V2 (`vector_store`, dimension via placeholder)
- [x] Auth: registration (always STUDENT), login/logout, BCrypt, 3 roles, admin bootstrap from env, CSRF on
- [x] User management API (list/search, create STUDENT/FACULTY, change role, activate/deactivate; not on self)
- [x] Document management: upload, validation (ext, MIME, magic bytes, PDFBox open, page cap), SHA-256 duplicate check,
      replace (version++), reindex/retry, delete (vectors + file), metadata edit (syncs vector metadata)
- [x] Ingestion: PDFBox per-page extraction, cleaning, heuristic table conversion, chunking, embed + store, status +
      processing log, partial-vector cleanup on failure, restart recovery of stuck PROCESSING
- [x] Retrieval with audience metadata filter + DB re-check; similarity threshold and top-K configurable
- [x] RAG: optional follow-up rewrite, prompt, JSON answer parsing, source-number validation, backend-built citations,
      deterministic not-found (no LLM call when nothing retrieved), daily quota, LLM failure => 503 and nothing saved
- [x] Chat API: conversations, messages with sources, delete, ask; feedback API
- [x] Admin API: stats, unanswered questions, feedback list
- [x] Thymeleaf UI: login, register, chat (sidebar, citations drawer, feedback), admin dashboard, documents, users
- [x] Dockerfile, docker-compose.yml, `.env.example`
- [x] Tests written: Chunker, TextCleaner, AccessPolicy, AnswerParser, RagService (Mockito), VectorStore integration
      (Testcontainers) — **none executed through Maven yet**
- [x] Docs: README, docs/*.md, DECISIONS.md

## Not implemented / pending

- [~] Evaluation: `eval/questions.tsv` (32 template questions) and `eval/Evaluate.java` written; compiles with javac, never run; Hit@K/MRR not supported
- [ ] Controller-level security tests (student cannot call `/api/admin/**`, cannot read others' conversations)
- [ ] Table extraction with Tabula-java (deliberately replaced by a heuristic, see DECISIONS.md D-06)
- [ ] OCR for scanned PDFs (out of scope; scanned PDFs are detected and marked FAILED)
- [ ] Hybrid search, reranking, streaming answers (optional extras)
- [ ] Login/IP rate limiting (only a per-user daily question quota exists)
- [ ] Deactivated users keep their current session until it expires (no per-request active check)
- [ ] Online deployment, HTTPS, backups (`docs/deployment.md` is guidance only)
- [ ] Screenshots

## Known risks to check first when you build

1. Spring AI 1.1.8 class/method names were written from memory (docs hosts were blocked). Most likely to need a tweak:
   `SearchRequest.builder()`, `FilterExpressionBuilder.in(...)`, `VectorStore.delete(Filter.Expression)`,
   `AssistantMessage.getText()`, the fake `EmbeddingModel` in the integration test.
2. Hibernate `ddl-auto: validate` vs. the SQL (types such as `smallint` ↔ `Short`).
3. `spring-boot-starter-parent 3.5.7` / `springdoc 2.8.9` / `pdfbox 3.0.5` versions exist and are compatible.
4. The OpenAI-compatible provider settings in `.env.example` for whichever free provider you choose.

## External credentials / configuration still required

- `AI_API_KEY` and a provider whose **current** free-tier limits and terms you have checked yourself
  (the Gemini free embedding tier showed 429 problems in forum results; not verified either way).
- `EMBEDDING_DIMENSIONS` matching the embedding model, set BEFORE the first run (fixed in `vector_store`).
- `ADMIN_EMAIL` / `ADMIN_PASSWORD` for the first admin.
- Docker (for Compose and Testcontainers) and Maven Central access on your machine.

## Exact next action

On your machine: `cp .env.example .env`, fill it in, run `mvn -q verify`. Fix compile errors (see risks above), then
`mvn test`, then `docker compose up --build`, log in as the admin, upload a PDF and ask a question. Update this file
with what really happened.
