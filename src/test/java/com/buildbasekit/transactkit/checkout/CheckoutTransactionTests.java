package com.buildbasekit.transactkit.checkout;

import com.buildbasekit.transactkit.exception.ConflictException;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.payment.PaymentStatus;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "stripe.secret-key=sk_test_unit")
class CheckoutTransactionTests {

    @Autowired
    private CheckoutService service;
    @Autowired
    private PaymentRepository payments;
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripe;

    @BeforeEach
    void cleanDatabase() {
        payments.deleteAll();
    }

    @Test
    void keepsRetrievedPaidStateWhenCheckoutRequestReturnsConflict() throws Exception {
        Payment payment = new Payment("order-paid-refresh", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_paid_refresh", "pi_pending", null, "attempt-1");
        payments.save(payment);

        Session paid = new Session();
        paid.setId("cs_paid_refresh");
        paid.setStatus("complete");
        paid.setPaymentStatus("paid");
        paid.setPaymentIntent("pi_paid");
        paid.setCustomer("cus_paid");
        when(stripe.v1().checkout().sessions().retrieve("cs_paid_refresh")).thenReturn(paid);

        CheckoutRequest retry = new CheckoutRequest(2000, "usd", "Order", "order-paid-refresh", "attempt-2",
                null, null, Map.of());
        assertThatThrownBy(() -> service.create(retry))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been paid");

        Payment synchronizedPayment = payments.findById(payment.getId()).orElseThrow();
        assertThat(synchronizedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(synchronizedPayment.getStripePaymentIntentId()).isEqualTo("pi_paid");
        assertThat(synchronizedPayment.getStripeCustomerId()).isEqualTo("cus_paid");
    }
}
