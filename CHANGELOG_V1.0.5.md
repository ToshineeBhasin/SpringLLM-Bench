# SpringLLM Java Research Suite v1.0.5

This release consolidates the fixes validated during the Windows pilot run.

## Included fixes

- Anthropic multi-block/thinking responses: preserve the visible final answer instead of accidentally materializing an empty first block.
- Failed provider generations: remove incomplete sample directories so a provider/API failure does not reserve a research sample ID.
- Provider billing failures: fail fast for exhausted credits instead of repeatedly retrying a non-transient billing condition.
- Windows Maven execution: invoke Maven through `cmd.exe /c mvn` (or `mvnw.cmd`) when evaluation runs from Java `ProcessBuilder`.
- Generated-project build isolation: use `-Dmaven.test.skip=true` so model-generated tests do not decide whether the application itself builds; independent benchmark tests remain in the evaluator module.
- Surefire result collection: parse `target/surefire-reports/*.xml` rather than Maven console text, so quiet Maven runs record actual functional/security/reliability test totals.
- Stale report protection: delete the previous tagged Surefire report before each functional/security/reliability test-group run.

## Research-integrity behavior

Generated application source is never automatically repaired before evaluation. Build/startup/test failures from the raw one-shot output remain experiment evidence.
