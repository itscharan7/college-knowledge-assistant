# RAG-Based College Knowledge Assistant

Ask natural-language questions about your college's official PDF documents (regulations, calendars, fee circulars,
placement rules…). Answers are generated **only** from those documents, each with a clickable source (document + page).
If the documents don't cover the question, the assistant says so instead of guessing.

> **Status: code complete, not yet built or run.** It was written in a sandbox that could not download Maven
> dependencies or run Docker. Read [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) for exactly what is and is not verified.
> Expect to fix a few compile errors on first build (version-sensitive Spring AI calls).

## Features

- Roles: STUDENT (self-register), FACULTY (created by admin), ADMIN (bootstrapped from environment variables)
- Admin: upload / replace / reindex / retry / delete PDFs, set category and audience, see processing logs, manage users,
  dashboard (documents, users, questions, unanswered, feedback), unanswered-question list
- Document visibility (ALL, STUDENT, FACULTY, ADMIN) enforced in the vector query *before* the LLM sees anything
- Async ingestion: PDFBox page-by-page extraction → cleaning (+ heuristic tables) → chunking → embeddings → PGVector
- Chat with history, follow-up questions, citations drawer ("Open PDF at this page"), 👍/👎 feedback, daily quota
- Provider-neutral AI code (Spring AI `ChatModel` / `EmbeddingModel` / `VectorStore`)

## Stack

Java 17+, Spring Boot 3.5, Spring AI 1.1, PDFBox 3, PostgreSQL + pgvector, Flyway, Spring Security, Thymeleaf,
Bootstrap 5 (CDN), vanilla JS, Maven, Docker Compose, JUnit 5 / Mockito / Testcontainers.

## Requirements

JDK 17+, Maven 3.9+, Docker (for Postgres/pgvector and Testcontainers), an API key for an OpenAI-compatible provider
(chat + embeddings). **Check the provider's current free-tier limits and terms yourself.**

## Setup

```bash
cp .env.example .env        # then edit .env (never commit it)
```

Set at least `POSTGRES_PASSWORD`/`DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `AI_API_KEY`, and make
`EMBEDDING_DIMENSIONS` match your embedding model **before the first run** (it is fixed in the `vector_store` table).

### Run with Docker Compose

```bash
docker compose up --build
```

Open http://localhost:8080 and sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

### Run from the IDE / Maven (database in Docker)

```bash
docker compose up -d db
set -a; source .env; set +a      # export the variables
mvn spring-boot:run
```

## First use

1. Sign in as admin → **Documents** → *Upload PDF* (choose category and audience). Status goes
   UPLOADED → PROCESSING → READY (or FAILED; open *Details* for the reason, then *Retry*).
2. Register a student at `/register` (or create faculty under **Users**), sign in, ask a question.
3. Click a source chip to see the excerpt and open the PDF at the cited page.

## Tests

```bash
mvn test                                        # unit tests (+ integration test if Docker is running)
mvn -Dtest=VectorStoreIntegrationTest test      # Flyway + pgvector + audience filter (needs Docker)
```

## API docs

Swagger UI at `/swagger-ui.html` (admin only). Summary in [docs/api.md](docs/api.md).

## Documentation

[architecture](docs/architecture.md) · [database](docs/database.md) · [api](docs/api.md) · [rag](docs/rag.md) ·
[testing](docs/testing.md) · [deployment](docs/deployment.md) · [viva](docs/viva.md) ·
[DECISIONS](DECISIONS.md) · [PROJECT_PROGRESS](PROJECT_PROGRESS.md)

## Screenshots

_Placeholders: login, chat with citations, source drawer, admin dashboard, documents, users._

## Limitations

- Scanned/image-only PDFs are detected and marked FAILED (no OCR).
- Table detection is a heuristic; complex tables may be read as plain text.
- Retrieval can miss; the LLM can still misread context. Accuracy has **not** been measured yet.
- Multi-document reasoning is weak; there is no real-time data.
- Depends on an external AI provider; free-tier rate limits can make ingestion and chat fail temporarily.
- Deactivating a user does not end their already-open session until it expires.
