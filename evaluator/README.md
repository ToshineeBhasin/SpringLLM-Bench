# SpringLLM Pilot Evaluator v1.1

This package contains black-box JUnit evaluators for the five pilot tasks.

## Why v1.1?
Before generating any samples, Tasks 2–5 were given deterministic creation/read endpoints so the hidden evaluator can set up and observe state without depending on an LLM's internal classes or database schema. This is a reproducibility change made **before data collection**, not after observing results.

## Run pattern

Start exactly one generated application for the task under evaluation, then run only that test class.

Task 1:
```bash
mvn -Dtest=Task1JwtRbacBlackBoxTest test
```

Task 2:
```bash
mvn -Dtest=Task2MoneyTransferBlackBoxTest test
```

Task 3:
```bash
mvn -Dtest=Task3InventoryConcurrencyBlackBoxTest test
```

Task 4:
```bash
mvn -Dtest=Task4ProductSearchBlackBoxTest test
```

Task 5:
```bash
mvn -Dtest=Task5KafkaIdempotencyBlackBoxTest -DkafkaBootstrap=localhost:9092 -DkafkaTopic=payment-events test
```

For a generated app on another port:
```bash
mvn -Dtest=Task2MoneyTransferBlackBoxTest -DbaseUrl=http://localhost:9090 test
```

## Research rules
- Preserve the raw generated output.
- Do not repair it before evaluation.
- Build failure, startup failure, and incomplete output are results.
- Use a fresh model context for every generation.
- Keep prompts byte-for-byte identical within a task/model comparison.
- Run manual/static review after black-box evaluation.
