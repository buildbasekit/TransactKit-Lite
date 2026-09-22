package com.buildbasekit.transactkit.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBusinessReference(String businessReference);
    Optional<Payment> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.id = :id")
    Optional<Payment> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.stripePaymentIntentId = :paymentIntentId")
    Optional<Payment> findByStripePaymentIntentIdForUpdate(String paymentIntentId);
}
