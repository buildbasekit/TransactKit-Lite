# TransactKit-Lite

TransactKit-Lite v1.0.0 is a focused Spring Boot payment foundation built around Stripe-hosted Checkout for one-time payments, with local payment tracking, refunds, and verified webhook synchronization.

[Website](https://buildbasekit.com/boilerplates/transactkit-lite/) · [Docs](https://buildbasekit.com/docs/)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://buildbasekit.com/boilerplates/transactkit-lite/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://buildbasekit.com/boilerplates/transactkit-lite/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Included

- Stripe-hosted Checkout Sessions for one-time payments
- Minimal Stripe Customer create and retrieve operations
- Local Payment and Checkout-attempt tracking with idempotency handling
- Full, partial, and repeated partial refunds
- Verified, retry-safe Stripe webhooks
- H2 development defaults and MySQL support
- Flyway migrations with Hibernate schema validation
- Built-in browser API tester and Postman collection
- Offline tests and optional stripe-mock contract verification
- AI-ready repository context

TransactKit-Lite intentionally excludes subscriptions, recurring billing, Stripe Billing, Connect, Terminal, invoicing, Tax, and product or price administration.

## Quick start

```bash
git clone https://github.com/buildbasekit/TransactKit-Lite.git
cd TransactKit-Lite
./mvnw spring-boot:run
```

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run`.

The application starts with an in-memory H2 database, so the first run does not require MySQL. Open the built-in API tester at [http://localhost:8080/api-test/index.html](http://localhost:8080/api-test/index.html). The application and tester start without Stripe credentials; Stripe-dependent operations require Stripe test credentials and return a controlled error while they are absent.

→ **[TransactKit-Lite quickstart](https://buildbasekit.com/docs/transactkit/quickstart/)**

## Documentation

- [Overview](https://buildbasekit.com/docs/transactkit/)
- [Quickstart](https://buildbasekit.com/docs/transactkit/quickstart/)
- [Configuration](https://buildbasekit.com/docs/transactkit/configuration/)
- [API Reference](https://buildbasekit.com/docs/transactkit/api/)
- [Webhooks](https://buildbasekit.com/docs/transactkit/webhooks/)
- [Architecture](https://buildbasekit.com/docs/transactkit/architecture/)
- [Testing](https://buildbasekit.com/docs/transactkit/testing/)

## Project context

- [`AGENTS.md`](AGENTS.md)
- [`AI_RULES.md`](AI_RULES.md)
- [`ARCHITECTURE.md`](ARCHITECTURE.md)
- [`AGENT_CONTRIBUTING.md`](AGENT_CONTRIBUTING.md)
- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`SECURITY.md`](SECURITY.md)

## BuildBaseKit

TransactKit-Lite is part of [BuildBaseKit](https://buildbasekit.com/) — focused Spring Boot foundations for reusable backend infrastructure.

## License

[MIT](LICENSE)
