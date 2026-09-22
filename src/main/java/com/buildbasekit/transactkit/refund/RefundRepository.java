package com.buildbasekit.transactkit.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<RefundRecord, UUID> {
    Optional<RefundRecord> findByStripeRefundId(String stripeRefundId);
    List<RefundRecord> findByPaymentId(UUID paymentId);
}
