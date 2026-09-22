# TransactKit Lite

TransactKit Lite is a focused Spring Boot foundation for Stripe-hosted one-time payments. It provides hosted Checkout, minimal Customer operations, local payment tracking, full and partial refunds, verified webhooks, and a dependency-free API test page—without wrapping the Stripe SDK or expanding into subscriptions.

## Features

- Stripe-hosted Checkout Sessions in `payment` mode
- Attempt-scoped idempotency with reusable open Sessions and replaceable expired attempts
- Minimal Stripe Customer create/retrieve operations
- Local payment and cumulative-refund state
- Full, partial, and multiple partial refunds tied to the Payment's stored PaymentIntent
- Checkout-centric, retry-safe webhook synchronization
- H2 quick start plus environment-configured MySQL support
- Flyway migrations with Hibernate schema validation
- Spring `ProblemDetail` errors and Jakarta Validation
- Plain HTML/CSS/JavaScript API test frontend

## Stack

- Java 25 LTS
- Spring Boot 4.1.1
- stripe-java 33.4.2
- Stripe API `2026-08-26.dahlia`, pinned by stripe-java 33.4.2

## Prerequisites and environment

Install Java 25. The Maven Wrapper supplies Maven. Copy `.env.example` to `.env` if local Stripe or database configuration is needed:

```properties
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_SUCCESS_URL=http://localhost:8080/api-test/success.html
STRIPE_CANCEL_URL=http://localhost:8080/api-test/cancel.html

DB_URL=jdbc:mysql://localhost:3306/transactkit_lite
DB_USERNAME=transactkit
DB_PASSWORD=change-me
```

All variables are optional. Without database variables, the app starts with in-memory H2. H2 data lasts for one application process and resets on restart. Without Stripe credentials, the app and frontend still start; Stripe-dependent operations return a controlled `503` and never use a fake key. The H2 console is disabled by default.

## Run and test

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Use `./mvnw` on macOS/Linux. Open <http://localhost:8080/api-test/index.html> for the local test frontend.

## Local stripe-mock contract tests

Start the locally installed official mock, then run the opt-in integration profile:

```powershell
C:\stripe-mock\start-stripe-mock.cmd
.\mvnw.cmd -Pstripe-mock clean verify
```

The profile uses the official Stripe SDK, dummy key `sk_test_123`, and `http://localhost:12111`. To point a manually started app at the mock, set `STRIPE_SECRET_KEY=sk_test_123` and `STRIPE_API_BASE=http://localhost:12111` for that process. Never set `STRIPE_API_BASE` in production.

stripe-mock validates supported request shapes and SDK serialization/deserialization. It is stateless and does not validate hosted Checkout, card processing, PaymentIntent/refund lifecycles, webhook delivery, realistic declines/errors, or Dashboard state; those require a real Stripe Sandbox.

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/configuration` | Report Stripe API/webhook configuration booleans |
| `POST` | `/api/customers` | Create a Stripe Customer |
| `GET` | `/api/customers/{id}` | Retrieve a Stripe Customer |
| `POST` | `/api/checkout/sessions` | Create or reuse a one-time Checkout attempt |
| `GET` | `/api/payments/{id}` | Read local payment state |
| `POST` | `/api/payments/{id}/refund` | Create a full or partial refund |
| `GET` | `/api/refunds/{id}` | Retrieve current refund state from Stripe |
| `POST` | `/api/stripe/webhook` | Receive a raw, signed Stripe event |

Checkout requests require both a stable `businessReference` for the logical payment and an `attemptReference` for one payment attempt. Repeating a request while its Session is open returns that Session; after expiration or failure, use a new attempt reference to create a new Session.

Import [TransactKit-Lite-API.postman_collection.json](TransactKit-Lite-API.postman_collection.json) for complete request examples and reusable variables.

## Stripe webhooks

Webhook endpoints in Stripe must use the API version compatible with the installed SDK: `2026-08-26.dahlia`. A version mismatch is rejected visibly because SDK model deserialization cannot be trusted across incompatible versions.

Forward the Lite events with the [Stripe CLI](https://docs.stripe.com/stripe-cli):

```powershell
stripe listen --events checkout.session.completed,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,checkout.session.expired,refund.created,refund.updated,refund.failed --forward-to localhost:8080/api/stripe/webhook
```

Put the CLI's `whsec_...` value in `STRIPE_WEBHOOK_SECRET`. Checkout collects payment details only on Stripe's hosted page; this application never accepts raw card data.

## Architecture and security boundary

The core call path is `Controller → application Service → StripeClient → Stripe`. Spring Data repositories persist only application-relevant payment, refund, and webhook projections. Stripe remains authoritative for Stripe resources. See [ARCHITECTURE.md](ARCHITECTURE.md).

The host application must add its own authentication, authorization, order ownership, fulfillment, and reconciliation. Lite intentionally excludes subscriptions, recurring billing, product/price management, invoices, Billing Portal, Tax management, Connect, Terminal, and other Stripe product families.
