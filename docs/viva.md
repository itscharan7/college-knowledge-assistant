# Viva questions (based on the code as written)

Be ready to say which parts you have personally run; some of this code was written but not yet built.

1. **Why RAG instead of a plain chatbot?** The LLM doesn't know your college's rules and may invent them. We retrieve the
   real text at question time, so answers are grounded and citable, and updating knowledge means uploading a PDF.
2. **What is an embedding?** A vector of numbers representing meaning; similar meaning ⇒ similar direction.
3. **How do you find relevant chunks?** Embed the question, run a nearest-neighbour search in PGVector (HNSW, cosine),
   take top-K above a similarity threshold (`RetrievalService`).
4. **Why PGVector?** Vectors live in the Postgres we already use: one DB, SQL + metadata filters, simple Docker setup.
5. **Why chunk, and why overlap?** Pages are too coarse; overlap keeps boundary-straddling facts whole (`Chunker`).
6. **How do you guarantee correct page numbers?** PDFBox extracts page by page; chunks never cross pages; the page number
   is stored in metadata and mapped by the backend, never produced by the LLM.
7. **How do you stop hallucinated citations?** The model returns only source numbers; `RagService` rejects numbers it
   didn't supply and answers without a valid source.
8. **How does "not found" work?** (1) nothing above threshold ⇒ canned refusal, no LLM call; (2) model says
   `answerable:false`, returns unparseable output, or cites nothing valid ⇒ same refusal.
9. **How do you enforce who sees which document?** Audience filter inside the vector query, a DB re-check of each hit,
   and a role check on the PDF endpoint; unauthorised reads return 404. Admin audience edits patch vector metadata at once.
10. **How do you handle prompt injection in PDFs?** Context is labelled untrusted data, the model has no tools, output
    shape and sources are validated, and only admins can upload.
11. **Why async ingestion?** Embedding is slow and rate-limited; upload returns 202 and the UI polls the status.
12. **What happens when ingestion fails?** Status FAILED, readable error, processing log, partial vectors deleted, admin can Retry.
13. **How is the system provider-agnostic?** Code depends on Spring AI `ChatModel`/`EmbeddingModel`/`VectorStore`; provider is
    configuration. Caveat: changing the embedding model requires re-embedding because dimensions/vector spaces differ.
14. **Why not Tabula?** It depends on PDFBox 2.x, which conflicts with PDFBox 3.x; a documented heuristic is used (DECISIONS D-06).
15. **How do you know it's accurate?** Not yet measured. Plan: golden set, Hit@K, MRR, citation accuracy, faithfulness,
    refusal rate (docs/testing.md).
16. **Limitations?** No OCR, heuristic tables, retrieval misses, LLM misreads, weak multi-document reasoning, external API
    dependence/rate limits, deactivated users keep an open session until expiry.
