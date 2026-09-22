package com.buildbasekit.transactkit.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "refunds")
public class RefundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String stripeRefundId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID paymentId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 64)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RefundRecord() {
    }

    public RefundRecord(String stripeRefundId, UUID paymentId, long amount, String status, String reason) {
        this.stripeRefundId = stripeRefundId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.reason = reason;
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

    public void synchronize(long amount, String status, String reason) {
        this.amount = amount;
        this.status = status;
        this.reason = reason;
    }

    public String getStripeRefundId() { return stripeRefundId; }
    public UUID getPaymentId() { return paymentId; }
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
