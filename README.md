# SpringLLM Java Research Suite v1.0.5

Java-only research orchestration for SpringLLM-Bench.

## Research paper and archived artifact

**Research paper**

Toshinee Bhasin, *Beyond Compilation: an Empirical Framework-aware Assessment of Security and Reliability in Spring Boot Applications Generated with Large Language Models*.

- Preprint DOI: https://doi.org/10.5281/zenodo.23066919

**SpringLLM-Bench research artifact**

- Software artifact DOI: https://doi.org/10.5281/zenodo.23066564
- GitHub release: https://github.com/ToshineeBhasin/SpringLLM-Bench/releases/tag/v1.0.5

The archived artifact contains the benchmark prompts, frozen generated Spring Boot applications, evaluator code, runner scripts, corrected evaluation results, static-analysis outputs, audit notes, and reproducibility documentation used in the pilot study.

### Pilot results

- 5 benchmark tasks
- 3 LLMs
- 3 generations per model-task cell
- 45 frozen generated applications
- 36 build successes
- 32 stable startups
- 31 functionally correct applications
- 3 validated static security findings
- 2 Functionally Correct but Security-Defective (FCSD) cases
- 0 confirmed Functionally Correct but Reliability-Defective (FCRD) cases

## Stack

- Java 21
- Spring Boot 4.0.8
- Spring AI 2.0.1
- JUnit 5 black-box evaluator
- OpenAI GPT-6 Sol (`gpt-6-sol`)
- Anthropic Claude Sonnet 5 (`claude-sonnet-5`)
- Google Gemini 3.8 Flash (`gemini-3.8-flash`)

Python is no longer required for generation, evaluation, or usage summaries.

## What the Java runner does

`generate`:

1. loads the frozen task prompt
2. calls each provider through Spring AI
3. records exact sample ID, model metadata, token usage, timestamps and SHA-256 hashes
4. preserves the raw model output
5. deterministically materializes `FILE: path` fenced blocks into `project/`

`evaluate`:

1. builds only the generated application code without repairing it (`maven.test.skip=true`; model-generated tests are excluded from the build gate)
2. starts its Spring Boot JAR
3. runs independent benchmark JUnit tests in separate `functional`, `security`, and `reliability` groups and parses their Surefire XML reports
4. writes FCSD/FCRD-ready CSV results

`summary`:

- aggregates provider-reported token usage by anonymized model ID

## Prerequisites

```text
java -version
mvn -version
```

Recommended validated Windows environment:

- JDK 21
- Maven 3.9.16
- PowerShell 5+ / PowerShell 7
- IntelliJ IDEA (optional for editing)
- Docker Desktop for the Kafka/T05 evaluation

`mvn -version` must report Java 21.

Set API keys in PowerShell:

```powershell
$env:OPENAI_API_KEY="..."
$env:ANTHROPIC_API_KEY="..."
$env:GOOGLE_API_KEY="..."
```

`GEMINI_API_KEY` is also accepted as a fallback for Google.

For Task 5 evaluation:

```powershell
$env:KAFKA_BOOTSTRAP="localhost:9092"
```

## Build the research suite

From the extracted root:

```powershell
mvn clean package -DskipTests
```

The runner JAR will be under `runner\target`.

## First smoke generation — T01 only, 3 models, 1 generation each

```powershell
java -jar runner\target\springllm-runner-1.0.0.jar `
  --springllm.command=generate `
  --springllm.work-root=. `
  --springllm.tasks=T01 `
  --springllm.models=M01,M02,M03 `
  --springllm.generations=1
```

Expected sample directories:

```text
generated/T01-M01-G01
generated/T01-M02-G01
generated/T01-M03-G01
```

Each contains raw output, hashes, metadata, materialization status and (if output format is valid) a generated `project/`.

## Token usage summary

```powershell
java -jar runner\target\springllm-runner-1.0.0.jar --springllm.command=summary --springllm.work-root=.
```

## Evaluate one sample

```powershell
java -jar runner\target\springllm-runner-1.0.0.jar `
  --springllm.command=evaluate `
  --springllm.work-root=. `
  --springllm.sample=T01-M01-G01
```

Then inspect:

```text
results/pilot-results.csv
generated/T01-M01-G01/evaluation/
```

## Pilot after smoke validation

The frozen pilot is:

```text
5 tasks × 3 models × 3 generations = 45 generated applications
```

Run all five tasks only after the 3-sample smoke run is operationally valid.

## Research integrity rules

- Fresh single-turn model calls only.
- No web/retrieval/tools/code execution are registered with Spring AI.
- Same frozen prompt bytes for each model within a task.
- Never manually repair generated code before evaluation.
- Raw response is preserved even if materialization/build fails.
- Existing sample directories are never overwritten.
- Functional, security, and reliability outcomes are evaluated separately.

## Windows evaluator notes (v1.0.5)

The Java runner resolves Maven on Windows through `cmd.exe /c mvn` (or a project `mvnw.cmd`). Evaluation uses independent tests from `evaluator/`; generated `src/test/java` files are intentionally not compiled as part of the application build gate.

## Citation

If you use or discuss SpringLLM-Bench, please cite the associated preprint and the archived software artifact.

**Paper**

Bhasin, T. (2026). *Beyond Compilation: an Empirical Framework-aware Assessment of Security and Reliability in Spring Boot Applications Generated with Large Language Models*. Zenodo. https://doi.org/10.5281/zenodo.23066919

**Software artifact**

Bhasin, T. (2026). *SpringLLM-Bench (v1.0.5)* [Software]. Zenodo. https://doi.org/10.5281/zenodo.23066564
