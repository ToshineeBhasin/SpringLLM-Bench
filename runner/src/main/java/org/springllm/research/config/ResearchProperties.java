package org.springllm.research.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "springllm")
public class ResearchProperties {
    private String command = "generate";
    private Path workRoot = Path.of(".");
    private List<String> tasks = List.of("T01");
    private List<String> models = List.of("M01", "M02", "M03");
    private int generations = 1;
    private int startGeneration = 1;
    private int maxOutputTokens = 32768;
    private int requestTimeoutSeconds = 900;
    private int maxRetries = 6;
    private int basePort = 18080;
    private String sample;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public Path getWorkRoot() { return workRoot; }
    public void setWorkRoot(Path workRoot) { this.workRoot = workRoot; }
    public List<String> getTasks() { return tasks; }
    public void setTasks(List<String> tasks) { this.tasks = tasks; }
    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }
    public int getGenerations() { return generations; }
    public int getStartGeneration() { return startGeneration; }
    public void setGenerations(int generations) { this.generations = generations; }
    public void setStartGeneration(int startGeneration) { this.startGeneration = startGeneration; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getBasePort() { return basePort; }
    public void setBasePort(int basePort) { this.basePort = basePort; }
    public String getSample() { return sample; }
    public void setSample(String sample) { this.sample = sample; }
}
