# Agent Guide

## Project map

- `config`: typed properties, conditional `StripeClient`, safe configuration status
- `checkout`: hosted Checkout and attempt lifecycle
- `customer`: minimal Stripe Customer operations
- `payment`: logical payment entity and read API
- `refund`: official Stripe refunds and cumulative local projection
- `webhook`: raw verification, durable claims, deduplication, synchronization
- `exception`: compact Spring `ProblemDetail` mapping
- `src/main/resources/db/migration`: Flyway schema
- `src/main/resources/static/api-test`: dependency-free API tester

## Commands

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Use `./mvnw` on macOS/Linux. The standard suite stays offline and requires no Stripe key, Stripe CLI, MySQL, browser, or internet.

## Change rules

1. Read `AI_RULES.md` and the relevant feature before editing.
2. Verify current official Spring, Java, Stripe, stripe-java, and Maven information before version or Stripe-behavior changes.
3. Keep the Checkout-first model: a logical Payment may have a replaceable latest Checkout attempt.
4. Use Spring/Jakarta/JPA/Hibernate/Java built-ins and the official Stripe SDK before custom code.
5. Keep `Controller → Service → StripeClient`; do not add generic gateways, adapters, providers, facades, or wrappers.
6. Keep schema changes in Flyway and Hibernate in `validate` mode. H2 quick start and MySQL compatibility must remain.
7. Preserve raw-body webhook verification, unique event claims, retryable `RECEIVED`/`FAILED`, constructor injection, and integer minor-unit money.
8. Put behavior in its feature package; do not expose entities or Stripe parameter objects from controllers.
9. Add focused offline tests, run targeted tests, then finish with `clean verify`.
10. Do not add Pro scope, authentication, Git operations, CI, Docker, or unrelated refactors.
