# Testing

**Honest status:** the tests below are written but have **not been executed through Maven** (no dependency access in the
authoring sandbox). Only the chunker/cleaner/table logic was run, in isolation (20 checks passed). Run `mvn test` and
record real results in PROJECT_PROGRESS.md.

## Automated tests (src/test)

| Test | Covers |
|---|---|
| `ChunkerTest` | size cap, overlap, page numbers kept, never crosses pages, sequential index, oversized sentence |
| `TextCleanerTest` | table → `Header: value` rows, header/footer removal, hyphen joining, near-empty page dropped |
| `AccessPolicyTest` | audience visibility per role |
| `AnswerParserTest` | JSON, markdown-fenced JSON, garbage |
| `RagServiceTest` (Mockito) | no chunks ⇒ refusal without LLM call; citations built from retrieval metadata; invented source numbers rejected; model refusal; unparseable output; persistence; LLM failure ⇒ 503 and nothing saved; daily quota; long question |
| `VectorStoreIntegrationTest` (Testcontainers, needs Docker) | real Flyway migrations + pgvector; audience filter; delete-by-document; metadata audience update without re-embedding. Embedding model is faked. |

## Not written yet

- Controller/security tests (student → `/api/admin/**` is 403; other user's conversation is 404; registration always STUDENT)
- PDF fixture tests (normal, multi-column, scanned, corrupt, password-protected)
- Full ingestion integration test (upload → READY → delete)

## RAG evaluation (tool written, never run)

`eval/questions.tsv` holds 32 TEMPLATE questions (answerable, table, follow-up, unanswerable, restricted, injection); edit titles/pages to match your real PDFs. Run against a running app with a STUDENT account:

```bash
java eval/Evaluate.java http://localhost:8080 student@example.com PASSWORD eval/questions.tsv
```

It reports correct refusals, answered-answerable and citation matches (compiles with javac; not run against the app). It cannot compute Hit@K or MRR because the API returns only cited sources; those need a retrieval-debug endpoint or log analysis. Plan and metric definitions:

Build a golden set of ≥30 questions on your real documents, each with the expected document + page:
answerable, unanswerable (target refusal > 90%), table-based, follow-ups, and restricted-audience questions.

| Metric | Meaning |
|---|---|
| Hit@K | expected page appears in the top-K retrieved chunks |
| MRR | average of 1/rank of the first correct chunk |
| Citation accuracy | cited page actually contains the answer |
| Faithfulness | every claim is supported by the context (manual or LLM-as-judge) |
| Refusal rate / false-refusal rate | unanswerable questions refused / answerable ones wrongly refused |
| Access-control correctness | restricted chunks never retrieved for lower roles |

Use it to choose chunk size (e.g. 300 vs 500) and the similarity threshold. Do not claim accuracy before measuring it.
