# Method note: Spring AI orchestration

The generation infrastructure is implemented in Java 21 using Spring AI 2.0.1. Provider-specific `ChatModel` implementations are configured manually so three providers can coexist in one process while preserving provider-specific model controls.

- OpenAI: `OpenAiChatModel`, model `gpt-6-sol`, reasoning effort `medium`.
- Anthropic: `AnthropicChatModel`, model `claude-sonnet-5`, adaptive thinking with medium effort.
- Google: `GoogleGenAiChatModel`, model `gemini-3.8-flash`, thinking level `MEDIUM`.

No tool callbacks, retrieval advisors, web search, or code-execution facilities are registered. Each generation is a single stateless `ChatModel.call(new Prompt(...))` invocation. Spring AI's unified response metadata is used to record prompt, completion, and total token counts.
