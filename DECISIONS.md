# DECISIONS

| # | Decision | Why |
|---|---|---|
| D-01 | **Spring Boot 3.5.x + Spring AI 1.1.x** (pinned BOM, never mixed) | Your locked stack says Boot 3.x. Spring AI 2.0 was still on milestones in search results, so 1.1.x is the stable line on Boot 3. Re-verify against the official compatibility matrix on day 1. |
| D-02 | **PGVector inside PostgreSQL** | One database for users, documents and vectors; SQL + metadata filters; easy Docker image (`pgvector/pgvector:pg16`). Plenty for thousands of chunks. |
| D-03 | **Flyway owns the vector table** (`initialize-schema: false`) | Production-style control. Dimension comes from a Flyway placeholder and is fixed once migrated. Changing the embedding model means a new migration + re-indexing. |
| D-04 | **Thymeleaf + Bootstrap 5 + vanilla JS** | Locked. One deployable, session auth with CSRF, no CORS/second toolchain. |
| D-05 | **PDFBox 3 directly** (not Spring AI's PDF reader) | Per-page control for exact page citations, easier to explain in the viva. |
| D-06 | **No Tabula-java; heuristic table converter instead** | Tabula-java 1.0.x depends on PDFBox 2.x, which conflicts with PDFBox 3.x on one classpath. Your prompt allows Tabula "if compatible". The `TableFormatter` heuristic turns runs of 3+ consistently-columned lines into `• Header: value; ...` rows. It is a heuristic: unusual layouts may not be detected (logged per document). **If you want true Tabula, tell me and we pin PDFBox 2.x instead; that is a change to a locked choice, so I did not make it unasked.** |
| D-07 | **Chunks never cross pages** | Every chunk has exactly one real page number, so citations are exact. Cost: a fact split across a page break is not joined. |
| D-08 | **Chunk size 500 tokens, overlap 60, 4 chars/token** (configurable) | Your starting values. Token counts are approximated by characters (no tokenizer dependency). To be tuned with the evaluation set. |
| D-09 | **Similarity threshold 0.5 is a placeholder** | Right value depends on the embedding model. Must be chosen by printing scores for answerable vs unanswerable questions (see docs/rag.md). |
| D-10 | **`ChatModel` + plain `Prompt` messages, not `ChatClient.system()/user()` text** | ChatClient's text API treats `{braces}` as template variables; PDF text and JSON examples contain braces. Plain messages are never templated. Still provider-neutral (Spring AI interface). |
| D-11 | **LLM returns JSON; backend builds citations** | The model only returns source *numbers*; the backend maps them to document/page from retrieval metadata and rejects numbers it never supplied. The LLM cannot invent a document or page. |
| D-12 | **Two-layer not-found** | (1) nothing above threshold => refuse with no LLM call (cheap, deterministic); (2) the model may set `answerable=false`; unparseable output or an answer without valid sources is also treated as not found. |
| D-13 | **Audience filter in the vector query + DB re-check** | Restricted text never reaches the LLM. Audience edits update vector metadata immediately (SQL JSON patch) without re-embedding. |
| D-14 | **Non-owners and unauthorised readers get 404, not 403** | Does not reveal that a restricted document or someone else's conversation exists. |
| D-15 | **`unanswered_questions` table (extra to your list)** | Lets admins see gaps without being able to read private conversations. |
| D-16 | **Replace = in-place new version; old vectors deleted at the start of re-processing** | Simple and fail-safe. A short window with no vectors exists during re-processing; a "zero-gap" swap would need version-tagged vectors (possible later). |
| D-17 | **Local-disk storage behind a `FileStorageService` interface** | Works with a Docker volume now; a hosted deployment without persistent disk can add an object-storage implementation without touching business code. |
| D-18 | **Provider via OpenAI-compatible Spring AI starter, configured by env** | Only officially supported integration used; any OpenAI-compatible endpoint is a config change. No provider has been verified as free/working (see PROJECT_PROGRESS.md). |
| D-19 | **Daily per-user question quota (default 50)** | Cost control on a free tier. Failed LLM calls are not saved and so do not count. |
