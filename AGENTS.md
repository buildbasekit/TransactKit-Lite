# AGENTS.md — AI Coding Agent Guide for TransactKit-Lite

This is the operating guide for coding agents and reviewers working on TransactKit-Lite v1.0.0. The project is intentionally focused: preserve its Checkout-first payment model, direct Stripe SDK integration, and readable package-by-feature structure.

## Repository snapshot

- **Application:** Spring Boot REST API
- **Version:** v1.0.0
- **Language:** Java 25
- **Spring Boot:** 4.1.1
- **Stripe SDK:** stripe-java 33.4.2, used through the official per-client `StripeClient`
- **Stripe API compatibility:** `2026-08-26.dahlia`, pinned by the installed SDK
- **Persistence:** in-memory H2 in MySQL compatibility mode by default; external MySQL is supported
- **Schema:** Flyway migrations; Hibernate `validate`
- **Payment scope:** Stripe-hosted Checkout in `payment` mode for one-time payments, minimal Customer operations, local Payment projections, full and partial refunds, and verified webhooks
- **Developer tools:** dependency-free browser API tester and Postman collection
- **Testing:** focused unit and Spring integration tests run offline; an opt-in stripe-mock profile checks supported SDK request/response contracts; real Stripe Test Mode verifies hosted and operational behavior

The application starts without Stripe credentials so its configuration status and browser tester remain available. Stripe-dependent operations fail explicitly until `STRIPE_SECRET_KEY` is configured; webhook receipt requires `STRIPE_WEBHOOK_SECRET`.

## Required reading order

Before changing code, read:

1. `README.md`
2. `AGENTS.md`
3. `AI_RULES.md`
4. `ARCHITECTURE.md`
5. `SECURITY.md`
6. The relevant implementation files
7. The relevant tests
8. `AGENT_CONTRIBUTING.md` and `CONTRIBUTING.md` before preparing a contribution

Do not infer payment behavior from generic Spring or Stripe examples. Verify the repository implementation and current official documentation first.

## Important repository paths

TransactKit-Lite uses package-by-feature, so controllers, services, DTOs, entities, and repositories live together in the feature they support.

```text
src/main/java/com/buildbasekit/transactkit/
├── checkout/   Checkout controller, request/response DTOs, and service
├── config/     Stripe properties, conditional StripeClient, configuration status
├── customer/   Customer controller, DTOs, and service
├── exception/  ProblemDetail exception mapping
├── payment/    Payment entity, statuses, repository, controller, DTO, and service
├── refund/     Refund entity, repository, controller, DTOs, and service
└── webhook/    Raw webhook controller, verification, claims, handlers, and persistence

src/main/resources/
├── application.properties
├── db/migration/       Flyway schema migrations
└── static/api-test/    Browser API tester

src/test/java/com/buildbasekit/transactkit/
└── application, HTTP, configuration, payment, refund, Checkout, Customer, and webhook tests

TransactKit-Lite-API.postman_collection.json
docs/testing/transactkit-lite-validation.md
```

## Core invariants

1. Stripe-hosted Checkout is the payment-input boundary. The application must never accept or store raw card numbers, CVCs, or equivalent payment credentials.
2. Stripe is authoritative for Customers, Checkout Sessions, PaymentIntents, and Refunds. The database stores only application-relevant projections and event-processing state.
3. One logical `Payment` is keyed by `businessReference` and may point to a replaceable latest Checkout attempt.
4. An open Session is reused for the same logical payment. Expired or failed attempts require a new `attemptReference`; settled payments cannot begin another attempt.
5. Checkout and refund idempotency keys remain attempt/reference scoped. Do not collapse them into a permanent business-reference key.
6. Money remains integer minor-unit `long` values with a three-letter currency code. Do not introduce floating-point monetary arithmetic.
7. Checkout creates the PaymentIntent. Do not add a competing direct PaymentIntent lifecycle.
8. Webhooks must be verified from the exact raw request body and `Stripe-Signature` using the Stripe SDK before any event is trusted.
9. The durable unique event claim is the duplicate-delivery boundary. `PROCESSED` and `IGNORED` are terminal; `RECEIVED` and `FAILED` remain retryable.
10. Missing Stripe credentials must produce explicit configuration failures, never simulated success or a fake production path.
11. Flyway owns schema changes and Hibernate remains in `validate` mode. H2 and MySQL compatibility must be preserved.
12. Controllers expose application DTOs, not JPA entities or Stripe parameter objects.

## Architecture boundary

Keep the main call path direct:

```text
Controller → Service → StripeClient → Stripe
                 ↘ Spring Data repository
```

Use Spring, Jakarta, JPA/Hibernate, Java, and the official Stripe SDK before adding custom infrastructure. Do not introduce a `PaymentProvider`, generic gateway interface, provider factory, facade, adapter stack, custom Stripe transport, or wrapper merely to make the Lite code look more enterprise-oriented.

Keep behavior in its feature package, use constructor injection, and keep controllers thin. Repositories remain persistence-focused; services own payment workflow and Stripe coordination.

## Future change expectations

Before changing any of these areas, trace both the synchronous request path and the later webhook path:

- **Checkout APIs:** preserve `businessReference`, `attemptReference`, amount/currency consistency, open-Session reuse, and replacement rules.
- **Stripe SDK usage:** verify the current official SDK and API-version behavior; retain `StripeClient` and SDK models, parameters, serialization, and signature helpers.
- **Payment lifecycle:** account for pending, paid, failed, partially refunded, and refunded states as well as late events from older Checkout attempts.
- **Webhooks:** preserve raw-body verification, durable claims, duplicate handling, rollback, retryability, and visible deserialization failures.
- **Refunds:** preserve the PaymentIntent relationship, payment locking, remaining-amount validation, reference-scoped idempotency, and cumulative succeeded-refund projection.
- **Persistence:** use a new Flyway migration for schema changes and confirm both H2 and MySQL behavior.
- **Configuration:** keep local H2 startup and explicit missing-Stripe failures; never expose secret values from the configuration endpoint.
- **Browser API tester and Postman:** update both when public endpoint contracts or workflows change.
- **Public documentation:** keep repository guidance and BuildBaseKit documentation synchronized with released behavior.

Subscriptions, recurring billing, Stripe Billing, Connect, Terminal, invoicing, Tax, product/price administration, authentication, fulfillment, and deployment infrastructure are outside the Lite boundary unless an explicit product-scope decision says otherwise.

## Completion expectations

For future code changes, add focused tests, run the relevant targeted checks, and finish with the repository's standard verification when possible. Report files changed, behavior and documentation changed, commands actually run, results, and anything not verified. Never claim a test or Stripe behavior was verified when it was not.
