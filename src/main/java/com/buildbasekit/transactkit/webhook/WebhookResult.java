package com.buildbasekit.transactkit.webhook;

public record WebhookResult(String eventId, String eventType, WebhookEventStatus status, boolean duplicate) {
}
