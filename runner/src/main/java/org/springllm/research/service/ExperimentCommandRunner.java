package org.springllm.research.service;

import org.springllm.research.config.ResearchProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExperimentCommandRunner implements ApplicationRunner {
    private final ResearchProperties properties;
    private final GenerationService generationService;
    private final EvaluationService evaluationService;
    private final SummaryService summaryService;

    public ExperimentCommandRunner(ResearchProperties properties, GenerationService generationService,
                                   EvaluationService evaluationService, SummaryService summaryService) {
        this.properties = properties;
        this.generationService = generationService;
        this.evaluationService = evaluationService;
        this.summaryService = summaryService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        switch (properties.getCommand().toLowerCase()) {
            case "generate" -> generationService.generate();
            case "evaluate" -> evaluationService.evaluate();
            case "summary" -> summaryService.printUsage();
            default -> throw new IllegalArgumentException("springllm.command must be generate, evaluate, or summary");
        }
    }
}
