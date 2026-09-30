# Patch 1.0.2

Fixed Spring AI 2.0.1 API compatibility errors found during the first local Maven build:

- `Usage` import changed to `org.springframework.ai.chat.metadata.Usage`.
- `GoogleGenAiChatModel` now uses the supported 2.0.1 builder API with `genAiClient(...)` and `options(...)` instead of the removed two-argument constructor.

These fixes follow the Spring AI 2.0.1 Javadocs. No benchmark prompt, model cohort, hidden test, or experimental rule was changed.
