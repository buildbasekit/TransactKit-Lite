# Contributing

Keep contributions focused on Stripe-hosted one-time payments and the existing package-by-feature design. Discuss broad product-scope changes before implementation.

## Development

- Use Java 25.
- Do not commit `.env`, Stripe keys, webhook secrets, card data, logs, or local database files.
- Prefer framework and Stripe SDK built-ins over custom infrastructure.
- Add or update meaningful offline tests with every behavior change.
- Run `.\mvnw.cmd clean verify` on Windows or `./mvnw clean verify` elsewhere.
- Update Flyway migrations and documentation when public behavior or persistence changes.

Pull requests should explain the problem, the scoped solution, and the verification performed. Do not include subscriptions, deployment infrastructure, authentication, or unrelated refactors.
