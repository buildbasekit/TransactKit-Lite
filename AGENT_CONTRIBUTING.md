# Agent Contribution Workflow

1. Inspect `AGENTS.md`, `AI_RULES.md`, the affected feature, and its tests.
2. Verify current official documentation before changing Spring, Java, Stripe, or dependency behavior.
3. Make the smallest scoped change that preserves the Lite boundary and direct `StripeClient` architecture.
4. Run focused offline tests for the changed behavior.
5. Run `.\mvnw.cmd clean verify` (or `./mvnw clean verify`).
6. Review all changed files for security, validation, transactions, migration compatibility, dead code, and accidental Pro scope.
7. Report behavior changed, verification performed, and only genuine unresolved issues.

Never use live credentials in tests or source files, perform Git operations, or add CI/Docker as part of a product change.
