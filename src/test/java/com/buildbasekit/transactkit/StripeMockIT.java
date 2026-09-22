package com.buildbasekit.transactkit;

import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.refund.RefundRepository;
import com.buildbasekit.transactkit.webhook.WebhookEventRepository;
import com.jayway.jsonpath.JsonPath;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StripeMockIT {

    @Autowired
    private MockMvc http;
    @Autowired
    private StripeClient stripe;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private RefundRepository refunds;
    @Autowired
    private WebhookEventRepository webhookEvents;

    @DynamicPropertySource
    static void stripeMockProperties(DynamicPropertyRegistry properties) {
        properties.add("stripe.secret-key", () -> "sk_test_123");
        properties.add("stripe.api-base",
                () -> System.getProperty("stripe.mock.url", "http://localhost:12111"));
        properties.add("stripe.checkout.success-url", () -> "http://localhost:8080/api-test/success.html");
        properties.add("stripe.checkout.cancel-url", () -> "http://localhost:8080/api-test/cancel.html");
    }

    @BeforeEach
    void cleanDatabase() {
        refunds.deleteAll();
        webhookEvents.deleteAll();
        payments.deleteAll();
    }

    @Test
    void customerCreateAndRetrieveUseOfficialSdkAgainstStripeMock() throws Exception {
        MvcResult created = http.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mock@example.com","name":"Mock Buyer",
                                 "description":"stripe-mock contract test","metadata":{"source":"integration"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        String customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        http.perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void checkoutRequestIsAcceptedAndPersistsLocalPayment() throws Exception {
        String request = """
                {"amount":2450,"currency":"USD","description":"Mock order",
                 "businessReference":"order-stripe-mock","attemptReference":"attempt-1",
                 "customerEmail":"mock@example.com","metadata":{"cart":"cart-42"}}
                """;
        MvcResult result = http.perform(post("/api/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.checkoutSessionId").isNotEmpty())
                .andReturn();

        UUID paymentId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.paymentId"));
        Payment payment = payments.findById(paymentId).orElseThrow();
        assertThat(payment.getAmount()).isEqualTo(2450);
        assertThat(payment.getCurrency()).isEqualTo("usd");
        assertThat(payment.getBusinessReference()).isEqualTo("order-stripe-mock");
        assertThat(payment.getStripeCheckoutSessionId()).isNotBlank();

        http.perform(post("/api/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.checkoutSessionId").value(payment.getStripeCheckoutSessionId()));
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void refundCreateAndRetrieveUseOfficialSdkAndUpdateLocalProjection() throws Exception {
        Payment payment = new Payment("refund-stripe-mock", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_mock_refund", "pi_mock_refund", null, "attempt-1");
        payment.markPaid("pi_mock_refund", null);
        payment = payments.save(payment);

        MvcResult created = http.perform(post("/api/payments/{id}/refund", payment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500,"reason":"REQUESTED_BY_CUSTOMER",
                                 "reference":"refund-mock-1","metadata":{"source":"integration"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andReturn();
        String refundId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        assertThat(refunds.count()).isEqualTo(1);
        assertThat(payments.findById(payment.getId()).orElseThrow().getRefundedAmount()).isPositive();
        http.perform(get("/api/refunds/{id}", refundId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void stripeMockRejectsAnInvalidCheckoutContract() {
        SessionCreateParams invalid = SessionCreateParams.builder()
                .putExtraParam("mode", "not-a-stripe-checkout-mode")
                .build();

        assertThatThrownBy(() -> stripe.v1().checkout().sessions().create(invalid))
                .isInstanceOf(InvalidRequestException.class);
    }
}
