package com.buildbasekit.transactkit.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String businessReference;

    @Column(length = 255)
    private String stripeCustomerId;

    @Column(unique = true, length = 255)
    private String stripeCheckoutSessionId;

    @Column(unique = true, length = 255)
    private String stripePaymentIntentId;

    @Column(length = 100)
    private String checkoutAttemptReference;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CheckoutStatus checkoutStatus;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long refundedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(String businessReference, long amount, String currency, String stripeCustomerId) {
        this.businessReference = businessReference;
        this.amount = amount;
        this.currency = currency;
        this.stripeCustomerId = stripeCustomerId;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public void attachCheckout(String checkoutSessionId, String paymentIntentId, String customerId,
                               String attemptReference) {
        if (isSettled()) {
            throw new IllegalStateException("A settled payment cannot start another Checkout attempt.");
        }
        this.stripeCheckoutSessionId = checkoutSessionId;
        this.stripePaymentIntentId = paymentIntentId;
        this.checkoutAttemptReference = attemptReference;
        this.checkoutStatus = CheckoutStatus.OPEN;
        this.status = PaymentStatus.PENDING;
        if (customerId != null) {
            this.stripeCustomerId = customerId;
        }
    }

    public void markCheckoutProcessing(String checkoutSessionId, String paymentIntentId, String customerId) {
        if (!isLatestCheckoutSession(checkoutSessionId) || isSettled()) {
            return;
        }
        checkoutStatus = CheckoutStatus.COMPLETE;
        if (paymentIntentId != null) {
            stripePaymentIntentId = paymentIntentId;
        }
        if (customerId != null) {
            stripeCustomerId = customerId;
        }
    }

    public void markPaid(String paymentIntentId, String customerId) {
        if (paymentIntentId != null) {
            this.stripePaymentIntentId = paymentIntentId;
        }
        if (customerId != null) {
            this.stripeCustomerId = customerId;
        }
        this.checkoutStatus = CheckoutStatus.COMPLETE;
        this.status = refundedAmount == 0 ? PaymentStatus.PAID
                : refundedAmount < amount ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED;
    }

    public void markCheckoutFailed(String checkoutSessionId) {
        if (isLatestCheckoutSession(checkoutSessionId) && status == PaymentStatus.PENDING) {
            checkoutStatus = CheckoutStatus.COMPLETE;
            status = PaymentStatus.FAILED;
        }
    }

    public void markCheckoutExpired(String checkoutSessionId) {
        if (isLatestCheckoutSession(checkoutSessionId) && !isSettled()) {
            checkoutStatus = CheckoutStatus.EXPIRED;
            status = PaymentStatus.PENDING;
        }
    }

    public void synchronizeRefundedAmount(long totalRefunded) {
        if (totalRefunded < 0 || totalRefunded > amount) {
            throw new IllegalStateException("Refunded amount is outside the payment amount.");
        }
        refundedAmount = totalRefunded;
        status = refundedAmount == 0 ? PaymentStatus.PAID
                : refundedAmount < amount ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED;
    }

    public boolean isLatestCheckoutSession(String checkoutSessionId) {
        return stripeCheckoutSessionId != null && stripeCheckoutSessionId.equals(checkoutSessionId);
    }

    public boolean isSettled() {
        return status == PaymentStatus.PAID || status == PaymentStatus.PARTIALLY_REFUNDED
                || status == PaymentStatus.REFUNDED;
    }

    public boolean canStartCheckoutAttempt() {
        return !isSettled() && (stripeCheckoutSessionId == null || checkoutStatus == CheckoutStatus.EXPIRED
                || status == PaymentStatus.FAILED);
    }

    public UUID getId() { return id; }
    public String getBusinessReference() { return businessReference; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeCheckoutSessionId() { return stripeCheckoutSessionId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public String getCheckoutAttemptReference() { return checkoutAttemptReference; }
    public CheckoutStatus getCheckoutStatus() { return checkoutStatus; }
    public long getAmount() { return amount; }
    public long getRefundedAmount() { return refundedAmount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
