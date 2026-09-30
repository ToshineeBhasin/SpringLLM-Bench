package org.springllm.research.model;

public record TestGroupResult(int total, int passed, int failed, int skipped, int exitCode) {
    public boolean allPassed() { return total > 0 && failed == 0 && exitCode == 0; }
}
