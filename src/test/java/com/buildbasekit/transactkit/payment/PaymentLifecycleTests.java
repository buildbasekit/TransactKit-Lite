package com.buildbasekit.transactkit.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentLifecycleTests {

    @Test
    void followsCheckoutAndRefundLifecycle() {
        Payment payment = new Payment("order-1", 2000, "usd", null);

        payment.attachCheckout("cs_1", "pi_1", "cus_1", "attempt-1");
        payment.markCheckoutProcessing("cs_1", "pi_1", "cus_1");
        payment.markPaid("pi_1", "cus_1");
        payment.synchronizeRefundedAmount(500);

        assertThat(payment.getCheckoutStatus()).isEqualTo(CheckoutStatus.COMPLETE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(500);

        payment.synchronizeRefundedAmount(2000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void expiredAttemptAllowsAReplacement() {
        Payment payment = new Payment("order-1", 2000, "usd", null);
        payment.attachCheckout("cs_1", null, null, "attempt-1");

        payment.markCheckoutExpired("cs_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCheckoutStatus()).isEqualTo(CheckoutStatus.EXPIRED);
        assertThat(payment.canStartCheckoutAttempt()).isTrue();
    }

    @Test
    void staleAttemptCannotFailOrExpireLatestAttempt() {
        Payment payment = new Payment("order-1", 2000, "usd", null);
        payment.attachCheckout("cs_latest", null, null, "attempt-2");

        payment.markCheckoutFailed("cs_old");
        payment.markCheckoutExpired("cs_old");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCheckoutStatus()).isEqualTo(CheckoutStatus.OPEN);
    }

    @Test
    void settledPaymentRejectsNewCheckoutAndImpossibleRefundTotals() {
        Payment payment = new Payment("order-1", 2000, "usd", null);
        payment.markPaid("pi_1", null);

        assertThatThrownBy(() -> payment.attachCheckout("cs_2", "pi_2", null, "attempt-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.synchronizeRefundedAmount(2001))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.synchronizeRefundedAmount(-1))
                .isInstanceOf(IllegalStateException.class);
    }
}
