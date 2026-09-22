package com.buildbasekit.transactkit.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByStripeEventId(String stripeEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEvent event where event.stripeEventId = :stripeEventId")
    Optional<WebhookEvent> findByStripeEventIdForUpdate(String stripeEventId);
}
