# Gemini model capacity failures

Production logs confirmed repeated `generateContent` HTTP 503 responses for `gemini-3.6-flash`, including final-answer failures. The client already retries that model twice with backoff. After those retries, it now tries the configured fallback generation model once. The default is `gemini-2.5-flash`; override with `GEMINI_FALLBACK_MODEL` or set it empty to disable fallback. The primary remains controlled by `GEMINI_MODEL`.

Fallback is only for an explicit HTTP 503 from generation. It does not replay the entire chat job, substitute a fabricated answer, change retrieval, or alter embedding models. Quota/authentication errors retain the existing handling; the fallback is not activated for 429, 400 or ambiguous network outcomes. If the alternative also fails, the existing safe error is returned. Provider outages affecting both models remain possible, and ordinary provider pricing/quotas apply to the alternative model.

Validation: 23 mocked Java tests passed across GeminiClient, routing, RAG generation and Redis worker tests on Java 25. New tests cover primary capacity failure recovery, bounded alternative failure, and no fallback on quota/invalid requests. A small live request confirmed the alternative model is accessible and produces text with the local configured credentials; production credentials must allow that model too. No user resume/query was sent for this probe.

Deploy the existing Spring service after merging. Repeat one failed question and inspect for `Gemini primary model unavailable; trying fallback model=...`, followed by a published answer. The old `cand_dev_101` cleanup warning is a separate stored-record cleanup problem; it is not the Gemini 503 cause and no database records were removed in this fix.
