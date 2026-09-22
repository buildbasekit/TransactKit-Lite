package com.buildbasekit.transactkit.refund;

import com.stripe.model.Refund;

import java.util.Map;
import java.util.UUID;

public record RefundResponse(
        String id,
        UUID paymentId,
        String paymentIntentId,
        long amount,
        String currency,
        String status,
        String reason,
        Map<String, String> metadata) {

    static RefundResponse from(Refund refund, UUID paymentId) {
        return new RefundResponse(refund.getId(), paymentId, refund.getPaymentIntent(), refund.getAmount(),
                refund.getCurrency(), refund.getStatus(), refund.getReason(), refund.getMetadata());
    }
}
