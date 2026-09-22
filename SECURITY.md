# Security Policy

TransactKit-Lite v1.0.0 provides a focused Stripe payment workflow, not a complete payment-security, authentication, or compliance solution. Review this boundary before exposing any endpoint in a production application.

## Reporting a vulnerability

Report suspected vulnerabilities privately to the BuildBaseKit project maintainers rather than opening a public issue. Include the affected version and behavior, reproducible steps, expected impact, and any suggested mitigation. Do not send live Stripe secrets, card data, customer data, or production webhook payloads.

Allow maintainers time to investigate and coordinate a fix before public disclosure.

## Stripe credentials

TransactKit-Lite reads Stripe credentials through Spring externalized configuration:

- `STRIPE_SECRET_KEY` enables Stripe API operations.
- `STRIPE_WEBHOOK_SECRET` enables webhook signature verification.
- `STRIPE_API_BASE` is an optional local stripe-mock routing override and must not be set in production.

Keep secret keys and webhook secrets in the deployment platform's environment or secret-management system. A local `.env` file may be used for development, but it must remain local and must never be committed. Restrict secret access, rotate exposed values promptly, and keep test and live credentials separated.

The configuration endpoint reports booleans only; it must never return credential values. When `STRIPE_SECRET_KEY` is absent, the application does not create a `StripeClient`, and Stripe-dependent services return an explicit configuration error rather than fake success.

## Card and payment-data boundary

Checkout is hosted by Stripe. TransactKit-Lite creates a Checkout Session and directs the customer to Stripe's hosted payment page; it must not collect, transmit through its own API, store, or log raw card numbers, CVCs, magnetic-stripe data, or equivalent payment credentials.

This architecture narrows the sensitive payment-input surface, but it does not by itself establish PCI compliance or any other certification. The deploying organization remains responsible for its integration, infrastructure, policies, and compliance obligations.

## Webhooks

`POST /api/stripe/webhook` must receive the exact raw request body. `StripeWebhookService` verifies that body and the `Stripe-Signature` header with `STRIPE_WEBHOOK_SECRET` through the Stripe SDK before dispatching the event.

After verification:

- each Stripe event ID is claimed under a database unique constraint;
- `PROCESSED` and `IGNORED` claims are terminal and duplicate deliveries are acknowledged without repeating work;
- `RECEIVED` and `FAILED` claims remain retryable;
- business state changes occur transactionally;
- a processing failure rolls back those changes and records a bounded failure message separately;
- unknown but valid events are recorded as `IGNORED`.

Do not bypass signature verification, parse a transformed body, implement custom webhook cryptography, or remove durable deduplication. Preserve safe behavior for retries, concurrent deliveries, out-of-order events, and failures after an event has been claimed.

Treat a replayed event as a duplicate at the Stripe event-ID boundary. Do not weaken the Stripe SDK's signature checks or the database uniqueness that prevents a terminal event from being processed twice.

Supported event objects must deserialize with stripe-java 33.4.2 and its pinned Stripe API version, `2026-08-26.dahlia`. Configure Stripe webhook endpoints with a compatible API version. Incompatible supported objects fail visibly so the event remains available for retry; do not coerce or silently ignore them.

## Checkout and idempotency

A local `Payment` represents one logical payment identified by `businessReference`. Its current Stripe Checkout attempt is represented by the latest Session ID and `attemptReference`.

- Repeating a request while the Session is open reuses that Session.
- An expired or asynchronously failed attempt may be replaced only with a new `attemptReference`.
- Paid, partially refunded, or refunded payments cannot start another Checkout attempt.
- Stripe Checkout idempotency keys use `checkout:<paymentId>:<attemptReference>`.

This model prevents accidental duplicate creation while allowing a failed attempt to be replaced. Future changes must preserve the distinction between a logical Payment and a specific Checkout attempt, including late webhook events from older attempts.

## Refunds

Refunds are created against the Payment's stored Stripe PaymentIntent. A refund request locks the Payment, accepts only paid or partially refunded state, validates the amount against the unrefunded balance, and uses `refund:<paymentId>:<reference>` as the Stripe idempotency key.

Full, partial, and repeated partial refunds are supported. Local `RefundRecord` rows retain the Stripe refund ID, related Payment ID, amount, status, reason, and timestamps. The Payment's refunded total is recalculated from locally recorded refunds whose Stripe status is `succeeded`; refund webhooks synchronize later state changes.

Do not detach a refund from its Payment, reuse one reference for different refund intent, exceed the remaining amount, or treat a pending/failed refund as succeeded. Production systems should reconcile local projections with Stripe and restrict who may initiate or inspect refunds.

## Local persistence and Stripe authority

Stripe remains authoritative for Customers, Checkout Sessions, PaymentIntents, and Refunds. The local database stores application-relevant projections:

- logical Payment identity, latest Checkout attempt, Stripe object IDs, amount, currency, refunded total, status, and timestamps;
- refund-to-Payment relationships and refund projection state;
- Stripe event IDs, event types, processing status, timestamps, and bounded error details.

Local records support application reads, concurrency control, idempotency, and webhook processing; they are not a substitute for Stripe's records. Define production reconciliation, retention, backup, access-control, and incident-recovery procedures appropriate to the host application.

Flyway owns the schema and Hibernate uses `validate`. Do not enable automatic schema mutation in place of reviewed migrations.

## Logging and error handling

Never log or expose:

- Stripe secret keys or webhook signing secrets;
- authorization headers or deployment environment secrets;
- raw card or payment credentials;
- unnecessary full webhook payloads or sensitive Stripe object contents;
- customer data that is not required for a documented operational purpose.

Keep error responses useful without including credentials, raw upstream responses, stack traces, or sensitive payloads. Limit access to production logs and define appropriate retention and redaction.

## Production responsibilities

TransactKit-Lite intentionally does not authenticate or authorize its Customer, Checkout, Payment, or Refund APIs. The host application is responsible for:

- authentication, authorization, tenant boundaries, and customer/order ownership;
- deciding who may create Checkout Sessions, retrieve records, or issue refunds;
- HTTPS, network controls, CORS policy, rate limiting, and abuse prevention;
- production secret storage, rotation, and least-privilege operational access;
- monitoring, alerting, audit retention, backup, and incident response;
- Stripe/local-state reconciliation and handling missed or delayed events;
- fulfillment logic that acts only on trusted, verified payment state;
- fraud controls, business validation, privacy, data retention, and regulatory obligations;
- production readiness and compliance review for the complete deployed system.

Do not present an unmodified TransactKit-Lite deployment as a complete secure payment application or a certified compliance solution.
