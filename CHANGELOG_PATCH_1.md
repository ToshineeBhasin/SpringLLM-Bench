# Patch 1 — Build Syntax Fixes

This patch fixes source-generation escaping defects in the research runner before any empirical samples are collected.

Fixed:
- escaped regex `\\d` in `EvaluationService` Surefire parser;
- newline and quote escaping in CSV writer/parser;
- CRLF/LF normalization in `ProjectMaterializer`;
- newline appending while reconstructing generated source files;
- path normalization when materializing generated project files.

No benchmark prompts, hidden test criteria, model cohort, or research hypotheses were changed.
