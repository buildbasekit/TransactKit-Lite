# AI Rules for TransactKit-Lite

These rules apply to coding agents making future changes to TransactKit-Lite v1.0.0.

1. Read `AGENTS.md`, `ARCHITECTURE.md`, `SECURITY.md`, the impacted implementation, and relevant tests before editing.
2. Preserve the documented Lite scope: Stripe-hosted Checkout for one-time payments, minimal Customer operations, local Payment projections, refunds, and verified webhooks. Do not invent features.
3. Keep `Controller → Service → StripeClient`. Do not add generic payment providers, gateways, factories, facades, adapter layers, custom Stripe HTTP, or needless wrappers.
4. Never accept, store, or log raw card numbers, CVCs, or equivalent payment credentials. Stripe-hosted Checkout remains the payment-input boundary.
5. Never hardcode or commit Stripe secret keys, webhook secrets, database passwords, `.env`, authorization headers, or deployment credentials.
6. Preserve Stripe webhook signature verification from the exact raw body. Use Stripe SDK verification and event models; do not implement webhook cryptography or trust unsigned event data.
7. Preserve durable webhook claims, uniqueness, retry semantics, transactional rollback, and terminal `PROCESSED`/`IGNORED` handling.
8. Preserve the logical Payment and replaceable latest Checkout-attempt model. Keep Checkout idempotency scoped to Payment plus `attemptReference` and refund idempotency scoped to Payment plus refund reference.
9. Keep monetary values as integer minor units using the existing `long` model. Never use floating point for payment or refund amounts.
10. Treat Stripe as authoritative for Stripe-owned objects and state. Keep local persistence limited to application-relevant projections.
11. Missing or unavailable Stripe configuration must fail clearly. Never return fake success, silently substitute credentials, or simulate Stripe state in production code.
12. Use Flyway for schema changes and keep Hibernate in `validate` mode. Preserve default H2 startup and MySQL compatibility.
13. Keep controllers thin, business workflow in feature services, persistence in repositories, and API contracts in DTOs. Do not expose JPA entities or Stripe parameter objects.
14. Do not silently weaken validation, exception mapping, configuration failures, webhook failures, or Stripe API error handling.
15. Do not add subscriptions, recurring billing, Stripe Billing, Connect, Terminal, invoicing, Tax, catalog administration, authentication, CI, Docker, or unrelated refactors through a scoped change.
16. When behavior changes, update focused tests, the browser API tester, Postman collection, repository documentation, and public API documentation wherever their contracts are affected.
17. Verify current official Spring, Java, Stripe, stripe-java, and Maven information before version or Stripe-behavior changes.
18. Keep diffs focused and report exactly what was changed, what was run, the results, and what remains unverified.
