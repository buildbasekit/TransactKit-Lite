package com.buildbasekit.transactkit.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String stripeEventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookEventStatus status;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private Instant processedAt;

    @Column(length = 1000)
    private String errorMessage;

    protected WebhookEvent() {
    }

    public WebhookEvent(String stripeEventId, String eventType) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.status = WebhookEventStatus.RECEIVED;
    }

    @PrePersist
    void created() {
        receivedAt = Instant.now();
    }

    public void processed() {
        status = WebhookEventStatus.PROCESSED;
        processedAt = Instant.now();
        errorMessage = null;
    }

    public void ignored() {
        status = WebhookEventStatus.IGNORED;
        processedAt = Instant.now();
        errorMessage = null;
    }

    public void failed(RuntimeException exception) {
        if (status == WebhookEventStatus.PROCESSED || status == WebhookEventStatus.IGNORED) {
            return;
        }
        status = WebhookEventStatus.FAILED;
        errorMessage = exception.getMessage() == null ? exception.getClass().getSimpleName()
                : exception.getMessage().substring(0, Math.min(exception.getMessage().length(), 1000));
    }

    public String getStripeEventId() { return stripeEventId; }
    public String getEventType() { return eventType; }
    public WebhookEventStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
