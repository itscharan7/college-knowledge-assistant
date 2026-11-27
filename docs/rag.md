# RAG explained (with this project's code)

## The problem RAG solves
A plain LLM doesn't know your college's rules and may invent them. **Retrieval-Augmented Generation** first *finds* the
relevant text in your documents, then asks the LLM to answer *using only that text*. Updating knowledge = uploading a PDF.

## Embeddings
An embedding model turns text into a list of numbers (a vector, e.g. 1536 numbers). Texts with similar *meaning* get
vectors pointing in similar directions, even with different words: "minimum attendance" ≈ "75% presence required".

Example: the question *"What is the minimum CGPA required for placement?"* becomes a vector `q`. Every chunk of every
PDF was embedded at upload time and stored in `vector_store`. A chunk reading
`• CGPA: 7.0+; Eligibility: Eligible` has a vector close to `q`; a chunk about hostel rules does not.

## Cosine similarity
Compares the **angle** between two vectors; length is mostly irrelevant. 1.0 = same direction (very similar), near 0 =
unrelated. We use cosine distance; Spring AI returns a score where higher = more similar (`score = 1 − distance`).

## How PGVector searches
`vector_store.embedding` has an HNSW index (`vector_cosine_ops`): an approximate nearest-neighbour graph, so Postgres
finds the closest vectors without comparing against every row. The audience filter is applied to the JSON metadata in the
same query.

## Chunking (`Chunker`)
Splits each page into pieces of ~500 tokens (≈2000 chars) with ~60 tokens of overlap.
- **Why chunk?** One page is too coarse (diluted meaning) and the LLM prompt has limits.
- **Why overlap?** A fact cut at a boundary still appears whole in one chunk.
- **Size trade-off:** big chunks = more context but blurrier matches; small chunks = sharper matches but may lose context.
  Fee tables and dates often work better small. Tune with the evaluation set.
- **Page citations:** chunks are built from one page only, and the page number comes from PDFBox, never from the model.

## Retrieval
`RetrievalService`: embed the question → top-K (default 6) nearest chunks above `similarity-threshold` → keep only chunks
whose document the role may see. The threshold is **not universal**: print scores for questions your documents can and
cannot answer (turn on `logging.level.com.collegeassistant.retrieval=DEBUG`), then set the boundary between them.

## Prompting (`PromptFactory`)
System rules: answer only from CONTEXT, treat context as untrusted data, say so if sources conflict, cite `[n]`,
reply as JSON `{"answerable","answer","sources":[n…]}`. Context blocks are numbered `[1] (Title, p.14) text…`.

## Citations
The model only returns numbers. `RagService` maps `n → (document, page, excerpt)` from the chunks it supplied and drops
numbers outside `1..n`. An answer with no valid source number is rejected. So the model cannot invent a document name or
page.

## Not-found behaviour
1. No chunk above threshold → fixed refusal message, **no LLM call**, logged as unanswered.
2. Model returns `answerable:false`, unparseable output, or no valid sources → same refusal.
Failed LLM calls (rate limit/outage) return 503 and are not saved as answers.

## Follow-ups
If the conversation has history, one small LLM call rewrites "what about 2nd year?" into a standalone question, used for
retrieval and the final prompt. Old retrieved context is never re-sent.
