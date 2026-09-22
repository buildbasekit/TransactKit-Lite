# TransactKit Lite validation

## Baseline

- Build: `./mvnw.cmd clean verify` passed on 2026-09-12 with Maven 3.9.16 and Oracle JDK 25.0.4.1.
- Tests: 56 run, 56 passed, 0 failed, 0 skipped.
- Startup: default `./mvnw.cmd spring-boot:run` started on port 8080 with in-memory H2; Flyway V1 migrated successfully; `/api/configuration` and `/api-test/index.html` returned HTTP 200 without Stripe credentials.
- Warnings: Flyway 12.4.0 reports H2 2.4.240 is newer than its verified H2 version; Mockito dynamically self-attaches on JDK 25; Hibernate reports the preferred Instant JDBC type setting as incubating. Expected duplicate-webhook constraint violations are logged by tests.
- Failures: none.
- Existing structural inconsistencies: TransactKit's root metadata and README are less aligned with AuthKit-Lite; the test console lacks the BuildBaseKit logo and current developer-console visual system; no intentional stripe-mock endpoint configuration or live mock integration suite exists.

## AuthKit-Lite comparison and migration checklist

Keep TransactKit's existing package-by-feature domain architecture and direct `Controller -> Service -> StripeClient` path. Align only reusable BuildBaseKit conventions:

- [x] Align safe root metadata, repository URL/SCM metadata, `.gitignore`, and concise first-run documentation where useful.
- [x] Preserve the matching Maven Wrapper 3.9.16, Java 25, Spring Boot 4.1.1, H2/MySQL/Flyway model, and `.env` import.
- [x] Update stripe-java from 33.4.0 to the current stable 33.4.2 after compatibility tests.
- [x] Add the official BuildBaseKit logo and adapt AuthKit-Lite's responsive developer-console visual language to TransactKit's actual API flows.
- [x] Add one centralized, opt-in Stripe API base URL setting for local stripe-mock use; keep the official SDK and Stripe's default API destination otherwise.
- [x] Inventory every endpoint and extend deterministic application-owned tests plus live stripe-mock contract tests.
- [x] Verify H2 fresh starts, MySQL configuration/SQL portability, browser widths, console/network activity, and complete regression.
- [x] Document stripe-mock limitations separately from the real Stripe Sandbox checklist.

## Phase results

### Phase 3 - project alignment

- Aligned Maven project URL/SCM metadata, root ignore conventions, and graceful-shutdown configuration with reusable AuthKit-Lite conventions.
- Updated stripe-java 33.4.0 to stable 33.4.2. The official release is a patch that rejects empty webhook secrets; the pinned Stripe API version remains `2026-08-26.dahlia`.
- Deliberately retained TransactKit's package-by-feature structure and existing dependencies; no authentication or Pro billing functionality was copied.

Validation: `./mvnw.cmd clean test` passed (56 run, 0 failed, 0 skipped); `git diff --check` passed and the diff contained only the intended root/configuration changes and this log.

### Phase 4 - H2 instant start

- Confirmed H2 2.4.240 remains the zero-configuration default in MySQL compatibility mode.
- Confirmed Flyway is the schema owner and Hibernate remains in `validate` mode.
- Confirmed a no-credential start migrates V1, starts port 8080, and serves both the API configuration endpoint and test frontend.
- Data is intentionally in memory: it survives for the life of one process (`DB_CLOSE_DELAY=-1`) and resets on application restart. No machine-specific data path is used.

Validation: covered by the baseline startup and the Phase 3 clean test run; no Phase 4 code change was needed.

### Phase 5 - MySQL support

- Confirmed MySQL is selected entirely through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; no Java source change or database-specific profile is required.
- Confirmed the MySQL driver 9.7.0 and Flyway MySQL module are present through Spring Boot dependency management.
- Reviewed V1 for portability: it uses portable `VARCHAR`, `BIGINT`, `TIMESTAMP(6)`, primary/foreign keys, and unique constraints without H2- or MySQL-only DDL.
- A local MySQL80 Windows service exists, but it exposed no TCP listener during validation and no test credentials were available. No live MySQL migration was claimed.

### Phase 6 - test frontend

- Reworked the dependency-free console using AuthKit-Lite's current BuildBaseKit visual language: official logo, dark/light themes, typography hierarchy, responsive workbench, endpoint cards, runtime ID state, status notices, loading/disabled states, timed response cards, reset/clear controls, and branded Checkout outcome pages.
- Kept the UI a developer tool with no framework, build system, remote script, card-data field, fake payment behavior, or Pro feature.
- Exposed every safe interactive flow (configuration, Customers, Checkout, local Payment lookup, Refund create/retrieve). Signed webhook delivery remains an API/testing concern because the browser must not receive the webhook secret.

Validation: JavaScript syntax check passed; `./mvnw.cmd clean test` passed (56 run, 0 failed, 0 skipped). Browser validation is recorded separately after the initial no-fix walkthrough.

### Phase 7 - API inventory

No endpoint requires authentication in the boilerplate. A host application must add authentication, authorization, resource ownership, and fulfillment policy.

| Method and path | Purpose / request / response | Validation and expected failures | Database / Stripe effects |
|---|---|---|---|
| `GET /api/configuration` | Reports only `stripeApiConfigured` and `webhookConfigured` booleans. | No request body. | Read-only; no Stripe call. |
| `POST /api/customers` | Accepts email, optional name/description/metadata; returns the application Customer DTO. | Email required/valid/max 254; name max 255; description max 500; metadata max 50 entries with bounded keys/values. Stripe rejection, auth, connection, and rate-limit errors use `ProblemDetail`. | No local write; calls Stripe Customer create. |
| `GET /api/customers/{id}` | Retrieves a Stripe Customer and returns the application Customer DTO. | ID required/max 255; invalid or missing Stripe IDs map to 400/404 as applicable. | No local write; calls Stripe Customer retrieve. |
| `POST /api/checkout/sessions` | Accepts minor-unit amount, currency, description, business/attempt references, optional Customer email/ID and metadata; returns local Payment UUID, Session ID, and hosted URL. | Positive amount; three-letter currency; bounded required references/description and metadata; validates conflicting reuse, settled payments, attempt state, and Stripe failures. | Creates/updates one logical Payment; retrieves an existing Session when present; otherwise creates a `payment`-mode Checkout Session with attempt-scoped SDK idempotency. |
| `GET /api/payments/{id}` | Returns the local Payment projection. | UUID parsing and missing records map to 400/404. | Local read only; no Stripe call. |
| `POST /api/payments/{id}/refund` | Accepts optional positive amount, optional supported reason, required reference and optional metadata; returns Refund DTO. Blank amount means full remaining amount. | UUID parsing; positive/bounded amount; missing/unpaid/no-PaymentIntent/conflicting state; Stripe failures. | Locks Payment, creates Stripe Refund with reference-scoped idempotency, upserts local RefundRecord, and recalculates cumulative successful refunds. |
| `GET /api/refunds/{id}` | Retrieves current Stripe Refund state and returns a Refund DTO, including a local Payment UUID when known. | ID required/max 255; invalid/missing Stripe IDs and Stripe failures map through `ProblemDetail`. | Reads local ID association; calls Stripe Refund retrieve. |
| `POST /api/stripe/webhook` | Accepts the exact raw JSON body plus required `Stripe-Signature`; returns event ID/type, local status, and duplicate flag. | Missing/invalid signature, missing secret, malformed/incompatible objects, and handler failures are rejected. Unknown verified event types are intentionally ignored. | Verifies with Stripe SDK, durably claims unique event ID, synchronizes supported Checkout/Refund projections transactionally, and records `PROCESSED`, `IGNORED`, or retryable `FAILED`. No outbound Stripe call. |

Supported journey: optional Customer creation -> Checkout Session -> local Payment record -> verified Checkout webhook state synchronization -> Payment lookup -> Refund create/retrieve -> verified Refund webhook synchronization. Customer creation is optional because Checkout also accepts an email or an existing Customer ID.

### Phase 8 - local stripe-mock

- Executable: `C:\\stripe-mock\\stripe-mock.exe`
- Launcher: existing `C:\\stripe-mock\\start-stripe-mock.cmd` (used unchanged)
- Initial integration run: failed before endpoint validation because `sk_test_stripe_mock` was not accepted as a valid-looking test key by stripe-mock 0.203.0. Fixed the test-only credential to the documented `sk_test_123`; no application code changed.
- Version: 0.203.0
- Port: HTTP 12111
- Startup: routed 419 paths / 594 endpoints and listened successfully on `[::]:12111`.
- Sanity request: `GET /v1/charges` with dummy Bearer credential returned HTTP 200, Stripe-style JSON, request ID, and `Stripe-Mock-Version: 0.203.0`.

### Phase 9 - intentional mock routing

- Added optional `STRIPE_API_BASE` -> typed `stripe.api-base` configuration.
- The single conditional `StripeClient` bean applies it through the official per-client `StripeClient.builder().setApiBase(...)` API.
- When unset, the SDK retains its normal `https://api.stripe.com` default. No global override, custom transport, or hard-coded local endpoint exists in Java code.

### Phase 10-13 - contract, webhook, and browser validation

- stripe-mock: customer create/retrieve, one-time Checkout creation, refund create/retrieve, SDK serialization/deserialization, local H2 writes, and invalid Checkout mode rejection passed through the official Stripe SDK.
- Application-owned coverage: request validation, idempotent open-session reuse, payment/refund projection, duplicate webhook claims, retryable `RECEIVED`/`FAILED`, concurrency, rollback, and supported event transitions passed.
- stripe-mock limitations observed: customer retrieval returns a stateless fixture; Checkout is not actually paid; refund creation cannot be exercised from a browser-created pending payment; hosted Checkout, real errors, lifecycle transitions, and webhook delivery remain Sandbox-only.
- Chrome: desktop (1440x900), tablet (768x1024), and mobile (390x844) layouts passed with one-column breakpoints and no horizontal overflow. Seven UI actions, loading lockout, required-field validation, response/status rendering, theme, clear/reset, and success/cancel pages passed. Console warnings/errors: 0.

### Phase 14 - confirmed issue list

#### SKL-001

- Severity: P1
- Area: webhook HTTP boundary
- Steps to reproduce: POST a correctly signed `not-json` body to `/api/stripe/webhook`.
- Expected: controlled 400 `ProblemDetail`; no event claim.
- Actual: 500 with a Gson `JsonSyntaxException` stack trace in application logs.
- Root cause: SDK JSON parsing failures from `Webhook.constructEvent` were not translated at the raw-body boundary.
- Proposed minimal fix: translate parsing-time runtime failures to `IllegalArgumentException` after signature verification and cover the HTTP result.

#### SKL-002

- Severity: P1
- Area: Checkout synchronization / transaction boundary
- Steps to reproduce: refresh an existing local Checkout attempt whose retrieved Stripe Session is paid, expired, or complete and then reaches an intentional conflict response.
- Expected: the retrieved Stripe state remains synchronized locally even though the new Checkout request returns 409.
- Actual: the unchecked `ConflictException` rolls back the surrounding transaction, including the synchronization write.
- Root cause: `CheckoutService.create` uses default rollback semantics for a conflict that is deliberately thrown after synchronization.
- Proposed minimal fix: keep local Stripe synchronization on `ConflictException` with `noRollbackFor`, verified through the proxied service and real H2 repository.

### Phase 15 - fixes

- SKL-001: fixed. Parsing-time runtime failures at `Webhook.constructEvent` are translated to a safe 400 after signature verification. Added signed-malformed, missing-signature, and malformed-request HTTP tests.
- SKL-002: fixed. Checkout conflicts no longer roll back Stripe state synchronized earlier in the transaction. Added a proxied service + real H2 repository regression test proving paid state persists after the 409.
- Focused regression: 10 tests passed (9 HTTP + 1 transaction); failures/skips: 0.

### Phase 16 - final automated regression

- `./mvnw.cmd -Pstripe-mock clean verify`: 64 tests run, 64 passed, 0 failed, 0 skipped (60 standard + 4 live stripe-mock integration tests).
- No test was disabled, ignored, or weakened. The expected duplicate-claim constraint warnings, Flyway/H2 verification warning, Hibernate incubating setting notice, and Mockito future-agent warning remain non-failing and are documented in the baseline.

### Phase 17 - fresh start

- Removed Stripe overrides from the new process and ran `./mvnw.cmd spring-boot:run` after the clean build.
- In-memory H2 initialized from empty state, Flyway applied V1, Hibernate validation passed, and Tomcat started on port 8080.
- `/api/configuration`: HTTP 200 with both configuration booleans `false`; `/api-test/index.html` and the BuildBaseKit logo: HTTP 200.
- No MySQL, Stripe key, IDE setting, username-based path, hidden variable, or previously generated data was required.

### Phase 18 - documentation

- Updated README stack values, H2 restart behavior, concise stripe-mock commands/configuration, and the mock-versus-Sandbox boundary.
- Updated architecture notes for per-client mock routing and deterministic webhook fixtures. The complete Postman collection already matched all public endpoints and required no request-contract change.

### Phase 19 - REQUIRES REAL STRIPE SANDBOX

- [ ] Create a real one-time Checkout Session and open the Stripe-hosted Checkout page.
- [ ] Complete a successful test-card payment and verify the final local Payment state.
- [ ] Exercise a failed test-card payment and any supported asynchronous payment path.
- [ ] Deliver every supported Checkout/refund webhook with a real Stripe signature.
- [ ] Redeliver one event and verify duplicate acknowledgement; observe realistic retry/order behavior.
- [ ] Verify local payment synchronization against Stripe Dashboard state.
- [ ] Create a partial refund, a second partial refund, and a full remaining refund; verify Stripe and local cumulative state.
- [ ] Confirm the completed/canceled redirect pages from real hosted Checkout.

### Phase 20 - final review

- `git diff --check` passed. Reviewed every changed path; changes are limited to root metadata/configuration, the developer console, two focused production fixes, tests, and documentation.
- Scans found no live/restricted keys, debug logging, TODO/FIXME markers, production hard-coded Stripe/mock URLs, unused custom abstractions, or accidental Pro implementation.
- Maven dependency analysis completed successfully. Its undeclared/unused report reflects normal Spring Boot starter aggregation and runtime-only H2/MySQL/Flyway modules; no dependency was removed or added merely to silence analyzer heuristics.
- The Postman collection still covers all eight public endpoints and stores no secrets.

## Final status

- Local application verification: complete.
- H2/Flyway fresh start: complete.
- MySQL configuration and portable DDL review: complete; live MySQL migration unavailable and not claimed.
- stripe-mock 0.203.0 contract verification: complete for supported request/response contracts.
- Real hosted payment/refund/webhook behavior: pending the checklist above.
- Verdict: **READY FOR REAL STRIPE SANDBOX VALIDATION**.
