# AI Agent Contribution Workflow for TransactKit-Lite

This guide describes how coding agents should approach future code changes to TransactKit-Lite v1.0.0. It complements `AGENTS.md`, `AI_RULES.md`, `ARCHITECTURE.md`, and `SECURITY.md`; it does not replace the human-facing `CONTRIBUTING.md`.

## Before changing code

1. Read the repository context in the order defined by `AGENTS.md`.
2. Restate the requested behavior and identify the affected feature packages and public contracts.
3. Inspect the impacted implementation and relevant tests before proposing a change.
4. Trace the existing Checkout-first lifecycle, including the synchronous Stripe call and later webhook synchronization.
5. Confirm that the request belongs in Lite and does not introduce an unrelated product family or speculative abstraction.
6. Verify current official documentation before changing Spring, Java, Stripe, stripe-java, Maven, or Stripe API-version behavior.

## Development workflow

- Make the smallest change that fully addresses the request.
- Preserve package-by-feature organization and the direct `Controller → Service → StripeClient` boundary.
- Prefer Spring, Jakarta, JPA/Hibernate, Java, and Stripe SDK capabilities already in use.
- Avoid unrelated cleanup, dependency additions, endpoint renames, and speculative refactoring.
- Keep public API compatibility where the task intends it; call out deliberate breaking changes explicitly.
- Put every database schema change in a new Flyway migration. Keep Hibernate `validate`, default H2 startup, and MySQL compatibility.
- Update DTO validation, exception behavior, browser tester flows, Postman examples, and documentation when their contract changes.
- Never use live credentials or customer data in code, fixtures, logs, examples, or reports.

## Payment-specific reasoning

Reason about the complete lifecycle rather than only the initiating HTTP request:

```text
Checkout request
→ Stripe Checkout Session
→ local Payment and latest Checkout-attempt state
→ Stripe-hosted payment lifecycle
→ verified Stripe webhook event
→ local Payment synchronization
→ Stripe Refund lifecycle
→ final local projection
```

For a relevant change, explicitly consider:

- repeated Checkout requests and Stripe idempotency keys;
- duplicate, concurrent, out-of-order, and retried webhooks;
- `RECEIVED` or `FAILED` event claims that need safe retry;
- completed-but-unpaid, asynchronously failed, and expired Checkout attempts;
- late events from a replaced Checkout attempt;
- full, partial, and repeated partial refunds;
- failed or pending refunds and cumulative succeeded-refund totals;
- Stripe authentication, rate-limit, request, connection, and API errors;
- absent Stripe API or webhook configuration;
- differences between Stripe authority and the local projection;
- transaction boundaries and Stripe/local recovery when either the remote operation or local synchronization fails.

Never accept raw card data or create a second PaymentIntent workflow beside Checkout. Preserve integer minor-unit amounts and the existing business, attempt, and refund references.

## Future verification expectations

Choose verification that matches the changed boundary:

### Normal tests

The standard unit and Spring integration suite is offline. Use focused tests while developing, then run the full Maven verification for a completed code change. Cover success, validation, conflict, missing-configuration, transaction, and failure paths relevant to the change.

```text
macOS/Linux: ./mvnw clean verify
Windows:     .\mvnw.cmd clean verify
```

### stripe-mock contract verification

Use the opt-in `stripe-mock` Maven profile only when a change affects a supported Stripe SDK request or response contract. It can check parameter serialization and model deserialization, but it is stateless and does not prove hosted Checkout, realistic PaymentIntent or refund lifecycles, webhook delivery, declines, Dashboard state, or operational error behavior.

```text
macOS/Linux: ./mvnw -Pstripe-mock clean verify
Windows:     .\mvnw.cmd -Pstripe-mock clean verify
```

### Real Stripe Test Mode

Use Stripe Test Mode or a sandbox when hosted Checkout, real event delivery and ordering, payment methods, asynchronous payment behavior, declines, refund lifecycle, Dashboard state, or API-version configuration must be validated. Use test credentials only, keep them local, and document the scenario without exposing secrets or sensitive payloads.

Do not claim one layer proves behavior that belongs to another. If a layer was not run, say so plainly.

## Documentation and contract updates

When behavior changes, review:

- `README.md`, `ARCHITECTURE.md`, `SECURITY.md`, and repository agent guidance;
- BuildBaseKit Overview, Quickstart, Configuration, API Reference, Webhooks, Architecture, and Testing pages;
- `TransactKit-Lite-API.postman_collection.json`;
- `src/main/resources/static/api-test/`;
- environment variable examples and configuration descriptions.

Keep TransactKit-Lite naming, v1.0.0 positioning, endpoint paths, version claims, configuration behavior, and supported scope synchronized.

## Review checklist

- [ ] The change stays within the requested scope and Lite boundary.
- [ ] Checkout, idempotency, webhook, refund, and persistence invariants remain intact.
- [ ] No secret, raw card data, unnecessary sensitive payload, or local environment file is included.
- [ ] Controllers, services, repositories, and DTOs retain their existing responsibilities.
- [ ] Schema changes use Flyway and remain valid for H2 and MySQL.
- [ ] Relevant tests and contract surfaces are updated.
- [ ] Verification results and omissions are reported accurately.
- [ ] Documentation matches the final behavior.
- [ ] The diff contains no unrelated formatting or product-scope expansion.

## Agent completion report

Every completed agent task should report:

```text
Summary
- Behavior changed and why.

Changed files
- path — purpose.

Documentation
- Contracts or guidance updated.

Verification
- Commands or manual checks actually performed and their results.
- Anything not run or not verified, with the reason.

Risk notes
- Security, migration, compatibility, Stripe, or follow-up concerns.
```

Do not hide failed checks, skipped verification, or uncertainty.
