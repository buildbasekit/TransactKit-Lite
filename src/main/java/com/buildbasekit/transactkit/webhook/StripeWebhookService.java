package com.buildbasekit.transactkit.webhook;

import com.buildbasekit.transactkit.config.StripeProperties;
import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookService {

    private final StripeProperties properties;
    private final StripeWebhookHandler handler;
    private final WebhookEventService eventService;

    public StripeWebhookService(StripeProperties properties, StripeWebhookHandler handler,
                                WebhookEventService eventService) {
        this.properties = properties;
        this.handler = handler;
        this.eventService = eventService;
    }

    public WebhookResult process(String payload, String signature) throws SignatureVerificationException {
        if (!properties.hasWebhookSecret()) {
            throw new StripeConfigurationException("Set STRIPE_WEBHOOK_SECRET before accepting webhooks.");
        }
        Event stripeEvent;
        try {
            stripeEvent = Webhook.constructEvent(payload, signature, properties.webhookSecret());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Webhook payload is not valid Stripe event JSON.", exception);
        }
        try {
            return handler.handle(stripeEvent);
        } catch (RuntimeException exception) {
            eventService.markFailed(stripeEvent.getId(), exception);
            throw exception;
        }
    }
}
