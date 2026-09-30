package org.springllm.research;

import org.springllm.research.config.ResearchProperties;
import org.springllm.research.service.ExperimentCommandRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ResearchProperties.class)
public class SpringLlmResearchApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(SpringLlmResearchApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
