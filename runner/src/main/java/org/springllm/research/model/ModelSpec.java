package org.springllm.research.model;

public record ModelSpec(String id, Provider provider, String label, String apiModelId) {
    public enum Provider { OPENAI, ANTHROPIC, GOOGLE }
}
