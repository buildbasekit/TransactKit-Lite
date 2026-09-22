package com.buildbasekit.transactkit.webhook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebhookEventService {

    private final WebhookEventRepository events;

    WebhookEventService(WebhookEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void claim(String eventId, String eventType) {
        events.saveAndFlush(new WebhookEvent(eventId, eventType));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(String eventId, RuntimeException exception) {
        events.findByStripeEventIdForUpdate(eventId).ifPresent(event -> event.failed(exception));
    }
}
