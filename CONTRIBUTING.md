# Contributing to TransactKit-Lite

Thank you for helping improve TransactKit-Lite v1.0.0. Contributions should keep the project focused, secure, readable, and useful as a Spring Boot foundation for Stripe-hosted one-time payments.

## Getting started

- Use Java 25 and the included Maven wrapper.
- Read `README.md`, `ARCHITECTURE.md`, and `SECURITY.md` before changing behavior.
- Coding agents and AI-assisted workflows must also follow `AGENTS.md`, `AI_RULES.md`, and `AGENT_CONTRIBUTING.md`.
- The default in-memory H2 database supports local development without MySQL. Stripe-dependent flows require Stripe test credentials.
- Never commit `.env`, Stripe keys, webhook secrets, database passwords, customer data, or local database files.

## Repository scope

TransactKit-Lite intentionally covers Stripe-hosted Checkout in `payment` mode, one-time payments, minimal Customer operations, local Payment tracking, refunds, and verified webhook synchronization.

Subscriptions, recurring billing, Stripe Billing, Connect, Terminal, Tax, invoicing, product/price administration, authentication, fulfillment, and deployment infrastructure should not enter Lite through unrelated pull requests. Discuss any deliberate product-scope change before implementation.

## Development expectations

- Keep changes focused and preserve package-by-feature organization.
- Maintain the direct `Controller → Service → StripeClient` architecture.
- Prefer existing Spring and official Stripe SDK capabilities over custom infrastructure.
- Keep controllers thin, workflow in services, persistence in repositories, and public contracts in DTOs.
- Avoid speculative abstractions, broad refactors, and unnecessary dependencies.
- Preserve backward compatibility unless the contribution explicitly and deliberately changes a contract.

## Payment and Stripe considerations

- Stripe-hosted Checkout remains the card-data boundary; never accept or store raw card details.
- Preserve the logical Payment and replaceable latest Checkout-attempt lifecycle.
- Keep Checkout and refund operations idempotent using the existing attempt/reference model.
- Verify webhooks from the raw body and handle duplicate and retried deliveries safely.
- Keep amounts in integer minor units and maintain Stripe/local-state synchronization.
- Treat Stripe as authoritative for Stripe-owned Customers, Checkout Sessions, PaymentIntents, and Refunds.
- Fail clearly when Stripe configuration or remote operations are unavailable; never fabricate success.

## Database migrations

Flyway owns the schema. Add a new migration for every schema change; do not edit an already released migration casually and do not switch Hibernate from `validate` to schema mutation. Migration changes must remain compatible with the default H2 MySQL mode and supported MySQL setup.

## Documentation

Update documentation whenever setup, environment variables, endpoint paths, request or response bodies, error behavior, payment state, webhook behavior, Stripe version assumptions, or database requirements change. Update the browser API tester and Postman collection when public workflows or contracts change.

## Testing future code changes

- Add focused offline tests for every behavior change.
- Run relevant targeted tests during development and the full Maven verification before submitting a code pull request.
- Use the opt-in stripe-mock profile only for supported Stripe SDK contract checks.
- Use Stripe Test Mode when hosted Checkout, real events, payment methods, declines, refunds, or Dashboard behavior must be verified.
- Report checks that were not run; never claim unperformed verification.

```text
macOS/Linux: ./mvnw clean verify
Windows:     .\mvnw.cmd clean verify
```

## Pull requests

A pull request should:

- explain the problem and the scoped solution;
- list user-visible, API, configuration, schema, and documentation effects;
- describe tests and external verification actually performed;
- identify anything not verified and any remaining risk;
- contain no secrets, customer data, generated clutter, or unrelated formatting changes;
- keep commits reviewable and action-oriented.

## Security reporting

Do not open a public issue for a suspected vulnerability. Follow the private reporting guidance in `SECURITY.md` and avoid including live secrets or customer data.
