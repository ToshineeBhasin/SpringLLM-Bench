package org.springllm.research.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springllm.research.config.ResearchProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SummaryService {
    private final ResearchProperties properties;
    private final ObjectMapper objectMapper;
    public SummaryService(ResearchProperties properties, ObjectMapper objectMapper) { this.properties = properties; this.objectMapper = objectMapper; }

    public void printUsage() throws Exception {
        Path generated = properties.getWorkRoot().toAbsolutePath().normalize().resolve("generated");
        Map<String,long[]> totals = new LinkedHashMap<>();
        if (!Files.isDirectory(generated)) return;
        try (var dirs = Files.list(generated)) {
            for (Path sample : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path metaPath = sample.resolve("metadata.json"); if (!Files.exists(metaPath)) continue;
                Map<String,Object> m = objectMapper.readValue(metaPath.toFile(), new TypeReference<>() {});
                String model = String.valueOf(m.getOrDefault("model_id","UNKNOWN"));
                long[] x = totals.computeIfAbsent(model,k->new long[4]); x[0]++;
                x[1]+=number(m.get("prompt_tokens")); x[2]+=number(m.get("completion_tokens")); x[3]+=number(m.get("total_tokens"));
            }
        }
        totals.forEach((model,x)-> System.out.printf("%s samples=%d promptTokens=%d completionTokens=%d totalTokens=%d%n", model,x[0],x[1],x[2],x[3]));
    }
    private static long number(Object o){ return o instanceof Number n ? n.longValue() : 0L; }
}
