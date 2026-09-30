package org.springllm.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springllm.research.config.ResearchProperties;
import org.springllm.research.model.ModelSpec;
import org.springllm.research.util.HashUtil;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class GenerationService {
    private static final Map<String, String> TASK_FILES = Map.of(
            "T01", "task01-jwt-rbac.txt",
            "T02", "task02-money-transfer.txt",
            "T03", "task03-inventory.txt",
            "T04", "task04-product-search.txt",
            "T05", "task05-kafka-consumer.txt"
    );

    private final ResearchProperties properties;
    private final ModelRegistry modelRegistry;
    private final ProjectMaterializer materializer;
    private final ObjectMapper objectMapper;

    public GenerationService(ResearchProperties properties, ModelRegistry modelRegistry,
                             ProjectMaterializer materializer, ObjectMapper objectMapper) {
        this.properties = properties;
        this.modelRegistry = modelRegistry;
        this.materializer = materializer;
        this.objectMapper = objectMapper;
    }

    public void generate() throws Exception {
        Path root = properties.getWorkRoot().toAbsolutePath().normalize();
        Path promptDir = root.resolve("prompts");
        Path generated = root.resolve("generated");
        Files.createDirectories(generated);

        for (String taskId : properties.getTasks()) {
            String fileName = TASK_FILES.get(taskId);
            if (fileName == null) throw new IllegalArgumentException("Unknown task: " + taskId);
            byte[] promptBytes = Files.readAllBytes(promptDir.resolve(fileName));
            String prompt = new String(promptBytes, StandardCharsets.UTF_8);
            String promptHash = HashUtil.sha256(promptBytes);

            for (String modelId : properties.getModels()) {
                ModelSpec spec = modelRegistry.spec(modelId);
                ChatModel model = modelRegistry.create(modelId);
                int start = properties.getStartGeneration();
		int end = start + properties.getGenerations() - 1;
		for (int generation = start; generation <= end; generation++) {
                    String sampleId = "%s-%s-G%02d".formatted(taskId, modelId, generation);
                    Path sampleDir = generated.resolve(sampleId);
                    if (Files.exists(sampleDir)) throw new IllegalStateException(sampleId + " already exists; refusing overwrite");
                    Files.createDirectories(sampleDir);
                    Files.write(sampleDir.resolve("prompt.txt"), promptBytes);
                    Files.writeString(sampleDir.resolve("prompt.sha256"), promptHash + System.lineSeparator());

                    Instant started = Instant.now();
                    ChatResponse response;
                    try {
                        response = callWithRetry(model, prompt, sampleId);
                    }
                    catch (Exception e) {
                        deleteRecursively(sampleDir);
                        throw e;
                    }
                    Instant finished = Instant.now();
                    String raw = extractVisibleText(response);
                    if (raw == null || raw.isBlank()) { System.err.println("DEBUG response metadata: " + response.getMetadata()); for (var g : response.getResults()) { if (g != null && g.getOutput() != null) { System.err.println("DEBUG output text: [" + g.getOutput().getText() + "]"); System.err.println("DEBUG output metadata: " + g.getOutput().getMetadata()); } }
                        deleteRecursively(sampleDir);
                        throw new IllegalStateException(sampleId + " provider response contained no visible final text");
                    }
                    Files.writeString(sampleDir.resolve("raw_output.txt"), raw, StandardCharsets.UTF_8);

                    Usage usage = response.getMetadata().getUsage();
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("sample_id", sampleId);
                    metadata.put("task_id", taskId);
                    metadata.put("model_id", modelId);
                    metadata.put("model_label", spec.label());
                    metadata.put("api_model_id_requested", spec.apiModelId());
                    metadata.put("response_model", response.getMetadata().getModel());
                    metadata.put("response_id", response.getMetadata().getId());
                    metadata.put("generation_number", generation);
                    metadata.put("started_utc", started.toString());
                    metadata.put("finished_utc", finished.toString());
                    metadata.put("reasoning_setting", "provider-native medium");
                    metadata.put("max_output_tokens", properties.getMaxOutputTokens());
                    metadata.put("prompt_sha256", promptHash);
                    metadata.put("raw_output_sha256", HashUtil.sha256(raw.getBytes(StandardCharsets.UTF_8)));
                    metadata.put("prompt_tokens", usage == null ? null : usage.getPromptTokens());
                    metadata.put("completion_tokens", usage == null ? null : usage.getCompletionTokens());
                    metadata.put("total_tokens", usage == null ? null : usage.getTotalTokens());
                    metadata.put("tools_enabled", false);
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(sampleDir.resolve("metadata.json").toFile(), metadata);

                    var materialized = materializer.materialize(raw, sampleDir);
                    System.out.printf("%s generated: responseModel=%s files=%d pom=%s errors=%d%n",
                            sampleId, response.getMetadata().getModel(), materialized.fileCount(),
                            materialized.hasPom(), materialized.errors().size());
                }
            }
        }
    }

    /**
     * Anthropic extended/adaptive thinking can return multiple generations/content
     * blocks. The first result may be a thinking/signature block whose text is
     * empty, so using ChatResponse#getResult() can discard the actual final answer.
     * Collect visible, non-thinking text blocks in response order. If a provider
     * does not expose thinking metadata, fall back to all non-blank text blocks.
     */
    private static String extractVisibleText(ChatResponse response) {
        StringBuilder visible = new StringBuilder();
        StringBuilder anyText = new StringBuilder();

        for (var generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) continue;
            var output = generation.getOutput();
            String text = output.getText();
            if (text == null || text.isBlank()) continue;

            appendBlock(anyText, text);

            Map<String, Object> metadata = output.getMetadata();
            boolean thinkingBlock = metadata != null && (
                    metadata.containsKey("thinking") ||
                    metadata.containsKey("signature") ||
                    metadata.containsKey("data")
            );
            if (!thinkingBlock) appendBlock(visible, text);
        }

        return !visible.isEmpty() ? visible.toString() : anyText.toString();
    }

    private static void appendBlock(StringBuilder target, String text) {
        if (!target.isEmpty()) target.append(System.lineSeparator());
        target.append(text);
    }

    private static void deleteRecursively(Path path) {
        try {
            if (!Files.exists(path)) return;
            try (var walk = Files.walk(path)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (Exception ignored) { }
                });
            }
        }
        catch (Exception ignored) { }
    }

    private ChatResponse callWithRetry(ChatModel model, String prompt, String sampleId) throws Exception {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<ChatResponse> future = executor.submit(() -> model.call(new Prompt(prompt)));
                try {
                    return future.get(properties.getRequestTimeoutSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    last = new RuntimeException("Provider request timed out for " + sampleId, e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) last = re;
                    else last = new RuntimeException(cause);
                    if (!looksTransient(last)) throw last;
                }
            }
            if (attempt >= properties.getMaxRetries()) break;
            long sleep = Math.min(60L, 1L << Math.min(attempt, 6));
            System.err.printf("Retrying %s after transient error (attempt %d/%d) in %ds: %s%n",
                    sampleId, attempt + 1, properties.getMaxRetries(), sleep, last == null ? "timeout" : last.getMessage());
            Thread.sleep(Duration.ofSeconds(sleep));
        }
        throw last == null ? new IllegalStateException("Generation failed") : last;
    }

    private static boolean looksTransient(Throwable t) {
        String s = (t.getClass().getName() + " " + String.valueOf(t.getMessage())).toLowerCase();

        // Billing/credit exhaustion will not recover through retries. Fail fast so
        // the operator can add provider credit without waiting through backoff.
        if (s.contains("no credits") || s.contains("credit balance is too low") ||
                s.contains("insufficient_quota") || s.contains("billing hard limit")) {
            return false;
        }

        return s.contains("transient") || s.contains("429") || s.contains("too many requests") ||
                s.contains("timeout") || s.contains("timed out") || s.contains("408") ||
                s.contains("409") || s.contains("500") || s.contains("502") ||
                s.contains("503") || s.contains("504") || s.contains("connection reset");
    }
}

