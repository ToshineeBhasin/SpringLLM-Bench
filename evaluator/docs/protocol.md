# Frozen Pilot Protocol v1.1

Pilot size: 5 tasks × 3 models × 3 generations = 45 applications.

Evaluation order:
1. Preserve raw output.
2. Attempt build.
3. Attempt startup.
4. Run task-specific black-box suite.
5. Run static analysis.
6. Perform manual/static review.
7. Record only evidence-backed findings.

This v1.1 protocol supersedes the earlier v1 benchmark because the original Tasks 2–5 lacked sufficient test-fixture/observation contracts for implementation-independent black-box testing. The revision was made before any model sample was collected.

Primary outcomes:
- build_success
- startup_success
- functional_correct
- confirmed_security_defect_count
- confirmed_reliability_defect_count
- FCSD
- FCRD
