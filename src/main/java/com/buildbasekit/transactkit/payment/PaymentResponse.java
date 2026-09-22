package com.buildbasekit.transactkit.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String businessReference,
        String stripeCustomerId,
        String stripeCheckoutSessionId,
        String stripePaymentIntentId,
        String checkoutAttemptReference,
        CheckoutStatus checkoutStatus,
        long amount,
        long refundedAmount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBusinessReference(), payment.getStripeCustomerId(),
                payment.getStripeCheckoutSessionId(), payment.getStripePaymentIntentId(),
                payment.getCheckoutAttemptReference(), payment.getCheckoutStatus(), payment.getAmount(),
                payment.getRefundedAmount(), payment.getCurrency(), payment.getStatus(), payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
