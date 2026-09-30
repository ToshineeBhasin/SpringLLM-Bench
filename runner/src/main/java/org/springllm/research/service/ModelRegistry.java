package org.springllm.research.service;

import com.anthropic.models.messages.OutputConfig;
import com.google.genai.Client;
import org.springllm.research.config.ResearchProperties;
import org.springllm.research.model.ModelSpec;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ModelRegistry {
    private final ResearchProperties properties;
    private final Map<String, ModelSpec> specs = new LinkedHashMap<>();

    public ModelRegistry(ResearchProperties properties) {
        this.properties = properties;
        specs.put("M01", new ModelSpec("M01", ModelSpec.Provider.OPENAI, "GPT-6 Sol", "gpt-6-sol"));
        specs.put("M02", new ModelSpec("M02", ModelSpec.Provider.ANTHROPIC, "Claude Sonnet 5", "claude-sonnet-5"));
        specs.put("M03", new ModelSpec("M03", ModelSpec.Provider.GOOGLE, "Gemini 3.8 Flash", "gemini-3.8-flash"));
    }

    public ModelSpec spec(String id) {
        ModelSpec spec = specs.get(id);
        if (spec == null) throw new IllegalArgumentException("Unknown model id: " + id);
        return spec;
    }

    public ChatModel create(String id) {
        ModelSpec spec = spec(id);
        return switch (spec.provider()) {
            case OPENAI -> createOpenAi(spec);
            case ANTHROPIC -> createAnthropic(spec);
            case GOOGLE -> createGoogle(spec);
        };
    }

    private ChatModel createOpenAi(ModelSpec spec) {
        String key = requiredEnv("OPENAI_API_KEY");
        var options = OpenAiChatOptions.builder()
                .apiKey(key)
                .model(spec.apiModelId())
                .maxCompletionTokens(properties.getMaxOutputTokens())
                .reasoningEffort("medium")
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .maxRetries(0)
                .store(false)
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    private ChatModel createAnthropic(ModelSpec spec) {
        String key = requiredEnv("ANTHROPIC_API_KEY");
        var options = AnthropicChatOptions.builder()
                .apiKey(key)
                .model(spec.apiModelId())
                .maxTokens(properties.getMaxOutputTokens())
                .thinkingAdaptive()
                .effort(OutputConfig.Effort.MEDIUM)
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .maxRetries(0)
                .build();
        return AnthropicChatModel.builder().options(options).build();
    }

    private ChatModel createGoogle(ModelSpec spec) {
        String key = firstNonBlank(System.getenv("GOOGLE_API_KEY"), System.getenv("GEMINI_API_KEY"));
        if (key == null) throw new IllegalStateException("Set GOOGLE_API_KEY or GEMINI_API_KEY");
        Client client = Client.builder().apiKey(key).build();
        var options = GoogleGenAiChatOptions.builder()
                .model(spec.apiModelId())
                .maxOutputTokens(properties.getMaxOutputTokens())
                .thinkingLevel(GoogleGenAiThinkingLevel.MEDIUM)
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(options)
                .build();
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is not set");
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
