package com.buildbasekit.transactkit.refund;

import com.buildbasekit.transactkit.exception.ConflictException;
import com.buildbasekit.transactkit.exception.ResourceNotFoundException;
import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.payment.PaymentStatus;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundService {

    private final ObjectProvider<StripeClient> stripeClients;
    private final PaymentRepository payments;
    private final RefundRepository refunds;

    public RefundService(ObjectProvider<StripeClient> stripeClients, PaymentRepository payments,
                         RefundRepository refunds) {
        this.stripeClients = stripeClients;
        this.payments = payments;
        this.refunds = refunds;
    }

    @Transactional
    public RefundResponse create(UUID paymentId, RefundRequest request) throws StripeException {
        StripeClient stripe = requireStripe();
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + paymentId + " was not found."));
        if (payment.getStripePaymentIntentId() == null) {
            throw new ConflictException("The payment does not have a Stripe PaymentIntent yet.");
        }
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ConflictException("Only paid or partially refunded payments can be refunded.");
        }

        long remaining = payment.getAmount() - payment.getRefundedAmount();
        long requestedAmount = request.amount() == null ? remaining : request.amount();
        if (requestedAmount <= 0 || requestedAmount > remaining) {
            throw new IllegalArgumentException("Refund amount must be positive and no greater than the unrefunded amount.");
        }

        Map<String, String> metadata = new HashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.put("businessReference", payment.getBusinessReference());
        metadata.put("refundReference", request.reference());

        RefundCreateParams.Builder builder = RefundCreateParams.builder()
                .setPaymentIntent(payment.getStripePaymentIntentId())
                .putAllMetadata(metadata);
        if (request.amount() != null) {
            builder.setAmount(requestedAmount);
        }
        if (request.reason() != null) {
            builder.setReason(toStripeReason(request.reason()));
        }

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("refund:" + paymentId + ":" + request.reference())
                .build();
        Refund refund = stripe.v1().refunds().create(builder.build(), options);
        synchronize(payment, refund);
        return RefundResponse.from(refund, paymentId);
    }

    public RefundResponse get(String id) throws StripeException {
        StripeClient stripe = requireStripe();
        Refund refund = stripe.v1().refunds().retrieve(id);
        UUID paymentId = refunds.findByStripeRefundId(id).map(RefundRecord::getPaymentId).orElse(null);
        return RefundResponse.from(refund, paymentId);
    }

    @Transactional
    public void synchronize(Refund refund) {
        if (refund.getPaymentIntent() == null) {
            return;
        }
        payments.findByStripePaymentIntentIdForUpdate(refund.getPaymentIntent())
                .ifPresent(payment -> synchronize(payment, refund));
    }

    private void synchronize(Payment payment, Refund refund) {
        RefundRecord record = refunds.findByStripeRefundId(refund.getId())
                .orElseGet(() -> new RefundRecord(refund.getId(), payment.getId(), refund.getAmount(),
                        refund.getStatus(), refund.getReason()));
        record.synchronize(refund.getAmount(), refund.getStatus(), refund.getReason());
        refunds.save(record);
        long refunded = refunds.findByPaymentId(payment.getId()).stream()
                .filter(candidate -> "succeeded".equals(candidate.getStatus()))
                .mapToLong(RefundRecord::getAmount)
                .sum();
        payment.synchronizeRefundedAmount(refunded);
        payments.save(payment);
    }

    private RefundCreateParams.Reason toStripeReason(RefundReason reason) {
        return switch (reason) {
            case DUPLICATE -> RefundCreateParams.Reason.DUPLICATE;
            case FRAUDULENT -> RefundCreateParams.Reason.FRAUDULENT;
            case REQUESTED_BY_CUSTOMER -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }

    private StripeClient requireStripe() {
        StripeClient stripe = stripeClients.getIfAvailable();
        if (stripe == null) {
            throw new StripeConfigurationException("Set STRIPE_SECRET_KEY before calling Stripe APIs.");
        }
        return stripe;
    }
}
