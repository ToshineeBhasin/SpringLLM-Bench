package org.springllm.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectMaterializer {
    private final ObjectMapper objectMapper;

    public ProjectMaterializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MaterializationResult materialize(String raw, Path sampleDir) throws IOException {
        Path projectDir = sampleDir.resolve("project");
        Files.createDirectories(projectDir);

        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        Map<String, String> files = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        int i = 0;
        while (i < lines.length) {
            if (!lines[i].startsWith("FILE: ")) {
                i++;
                continue;
            }

            String relative = lines[i].substring(6).trim();
            i++;
            if (i >= lines.length || !lines[i].startsWith("```")) {
                errors.add("Missing opening fence for " + relative);
                continue;
            }

            i++;
            StringBuilder body = new StringBuilder();
            while (i < lines.length && !lines[i].equals("```")) {
                if (body.length() > 0) body.append('\n');
                body.append(lines[i]);
                i++;
            }

            if (i >= lines.length) {
                errors.add("Missing closing fence for " + relative);
                break;
            }
            i++;

            if (relative.isBlank()) {
                errors.add("Empty file path");
                continue;
            }
            if (files.putIfAbsent(relative, body.toString()) != null) {
                errors.add("Duplicate file: " + relative);
            }
        }

        Path normalizedProjectDir = projectDir.toAbsolutePath().normalize();
        for (var entry : files.entrySet()) {
            Path out = normalizedProjectDir.resolve(entry.getKey()).normalize();
            if (!out.startsWith(normalizedProjectDir)) {
                errors.add("Rejected path traversal: " + entry.getKey());
                continue;
            }
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, entry.getValue(), StandardCharsets.UTF_8);
        }

        boolean hasPom = Files.exists(normalizedProjectDir.resolve("pom.xml"));
        MaterializationResult result = new MaterializationResult(files.size(), hasPom, List.copyOf(errors));
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(sampleDir.resolve("materialization.json").toFile(), result);
        return result;
    }

    public record MaterializationResult(int fileCount, boolean hasPom, List<String> errors) {}
}
