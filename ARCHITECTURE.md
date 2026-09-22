# Architecture

TransactKit Lite uses package-by-feature and a direct `Controller → Service → StripeClient` call path. Controllers expose application records, services own the small amount of payment business logic, and Spring Data JPA persists local projections. There are no Stripe gateways, adapters, generic wrappers, or custom HTTP clients.

## Checkout-first lifecycle

A `Payment` represents one logical application payment, keyed by `businessReference`. Its current Stripe Checkout attempt is represented by the latest Session ID, an `attemptReference`, and `CheckoutStatus`.

- An open Session is reusable for accidental retries.
- A completed unpaid Session remains processing while an asynchronous payment resolves.
- An expired or asynchronously failed attempt allows a later request with a new attempt reference.
- A paid or refunded logical payment cannot start another Checkout attempt.
- Stripe idempotency keys use `checkout:<paymentId>:<attemptReference>` rather than permanently binding a business reference to one Session.

Checkout creates the PaymentIntent. Lite never creates PaymentIntents directly and handles Checkout events rather than maintaining a competing PaymentIntent state machine.

## Persistence boundary

Stripe is authoritative for Checkout Sessions, Customers, PaymentIntents, and Refunds. The database stores only:

- `Payment`: business reference, latest attempt and Stripe IDs, amount/currency, cumulative refunded amount, state, timestamps
- `RefundRecord`: Stripe refund ID, local payment ID, amount, status, reason, timestamps
- `WebhookEvent`: unique Stripe event ID, type, processing status, timestamps, bounded error detail

Flyway owns the schema and Hibernate validates it. H2 in MySQL compatibility mode is the zero-configuration default; `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` provide the MySQL production path.

Outbound Stripe traffic uses the official per-client `StripeClient`. `STRIPE_API_BASE` is an opt-in local contract-test override for stripe-mock; when absent, the SDK retains Stripe's production API base. No endpoint URL is embedded in application code.

## Webhook transaction model

The exact raw payload and `Stripe-Signature` header are verified by Stripe's `Webhook.constructEvent`. A short independent transaction claims the unique Stripe event ID as `RECEIVED`. The business handler then locks that claim and performs state changes transactionally.

`PROCESSED` and `IGNORED` are terminal. `RECEIVED` and `FAILED` are retryable. A business-processing failure rolls back its state changes, then records `FAILED` in a separate transaction. The database unique constraint is the duplicate-delivery boundary; a unique-constraint race is treated as a duplicate and the existing row is locked before deciding whether to retry or acknowledge it.

Supported events are:

- `checkout.session.completed`
- `checkout.session.async_payment_succeeded`
- `checkout.session.async_payment_failed`
- `checkout.session.expired`
- `refund.created`
- `refund.updated`
- `refund.failed`

Unknown verified events are recorded as `IGNORED`. Supported events must deserialize through Stripe's SDK models using the pinned API version `2026-08-26.dahlia`; incompatible objects fail visibly and remain retryable.

stripe-mock does not deliver or model webhooks. Webhook signatures, deduplication, retry state, rollback, concurrency, and projection transitions are therefore tested with deterministic signed fixtures; real delivery and ordering remain Stripe Sandbox checks.

## Security and scope

The application never accepts raw card data or exposes Stripe secrets. Stripe ID inputs use only nonblank/length validation so Stripe remains the authority on identifier format. The configuration endpoint exposes booleans only. Host applications are responsible for authenticating and authorizing non-webhook endpoints.

Subscriptions, recurring billing, catalog management, invoices, Billing Portal, Connect, Tax, Terminal, queues, caches, deployment infrastructure, and authentication are outside Lite scope.
