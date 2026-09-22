# Security Policy

## Reporting

Please report suspected vulnerabilities privately to the BuildBaseKit project maintainers rather than opening a public issue. Include affected behavior, reproduction steps, and potential impact; do not include live secrets or customer data.

## Security boundary

TransactKit Lite verifies Stripe webhooks but intentionally does not authenticate its Customer, Checkout, Payment, or Refund APIs. A host application must add authentication, authorization, ownership checks, rate limits, and production access controls.

Never store Stripe secret keys, webhook secrets, card numbers, CVCs, or raw payment credentials in source control. Use environment variables and Stripe-hosted Checkout. Keep webhook endpoints configured to the Stripe API version pinned by the installed stripe-java SDK.
