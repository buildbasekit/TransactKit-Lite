# AI Rules

- Prefer Spring Boot, Spring MVC, Spring Data, Jakarta Validation, JPA/Hibernate, Java, and Stripe SDK built-ins.
- Verify current official Stripe documentation before changing Stripe behavior.
- Use `StripeClient` directly; no custom Stripe HTTP, transport, generic wrapper, or needless interface pair.
- Use Stripe SDK serialization, webhook verification, event models, and test signature helpers; never implement webhook cryptography.
- Keep package-by-feature, constructor injection, Checkout-first state, attempt-scoped idempotency, Flyway migrations, and Hibernate validation.
- Never accept, store, log, or process card numbers, CVCs, secret keys, or raw webhook payloads.
- Do not add subscriptions, recurring billing, Products/Prices administration, invoices, Billing Portal, Connect, or other Pro/product-family scope.
- Do not add unrelated abstractions or refactors.
- Every behavior change needs focused offline tests and a complete `clean verify` run.
- Never initialize or modify Git, add CI/Docker, create releases, or change unrelated system configuration.
