# Patch 1.0.3

- Fix Anthropic response extraction when adaptive thinking returns multiple ChatResponse results/content blocks.
- Extract visible non-thinking text from all generations instead of blindly using `ChatResponse#getResult()`.
- Fall back to non-blank text if provider metadata does not identify thinking blocks.
- Automatically remove incomplete sample directories when an API call fails before a valid response is captured.
- Treat a successful API response with no visible final text as an operational failure, not a research sample.

No prompts, hidden tests, model IDs, or research hypotheses were changed.
