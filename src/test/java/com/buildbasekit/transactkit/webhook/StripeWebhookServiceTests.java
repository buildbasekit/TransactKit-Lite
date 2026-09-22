package com.buildbasekit.transactkit.webhook;

import com.buildbasekit.transactkit.payment.CheckoutStatus;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.payment.PaymentStatus;
import com.buildbasekit.transactkit.refund.RefundRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StripeWebhookServiceTests {

    private static final String SECRET = "whsec_unit_secret";
    private static final String API_VERSION = "2026-08-26.dahlia";

    @Autowired
    private StripeWebhookService service;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private RefundRepository refunds;
    @Autowired
    private WebhookEventRepository events;

    @BeforeEach
    void cleanDatabase() {
        refunds.deleteAll();
        events.deleteAll();
        payments.deleteAll();
    }

    @Test
    void acceptsValidSignatureAndSynchronizesCheckoutSuccess() throws Exception {
        Payment payment = payments.save(new Payment("order-success", 2000, "usd", null));
        String payload = event("evt_checkout", "checkout.session.completed", """
                {"id":"cs_test_success","object":"checkout.session","client_reference_id":"order-success",
                 "payment_status":"paid","payment_intent":"pi_success","customer":"cus_success"}
                """);

        WebhookResult result = service.process(payload, signature(payload));

        Payment updated = payments.findById(payment.getId()).orElseThrow();
        assertThat(result.status()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(updated.getStripePaymentIntentId()).isEqualTo("pi_success");
    }

    @Test
    void completedUnpaidCheckoutStaysProcessingUntilAsyncSuccess() throws Exception {
        Payment payment = new Payment("order-async", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_async", "pi_async", null, "attempt-1");
        payments.save(payment);
        String completed = event("evt_async_complete", "checkout.session.completed", """
                {"id":"cs_async","object":"checkout.session","client_reference_id":"order-async",
                 "status":"complete","payment_status":"unpaid","payment_intent":"pi_async"}
                """);
        String succeeded = event("evt_async_success", "checkout.session.async_payment_succeeded", """
                {"id":"cs_async","object":"checkout.session","client_reference_id":"order-async",
                 "status":"complete","payment_status":"paid","payment_intent":"pi_async"}
                """);

        service.process(completed, signature(completed));
        Payment processing = payments.findById(payment.getId()).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(processing.getCheckoutStatus()).isEqualTo(CheckoutStatus.COMPLETE);

        service.process(succeeded, signature(succeeded));
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void asyncFailureOnlyChangesTheLatestCheckoutAttempt() throws Exception {
        Payment payment = new Payment("order-failed", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_latest", "pi_latest", null, "attempt-2");
        payments.save(payment);
        String oldFailure = event("evt_old_failed", "checkout.session.async_payment_failed", """
                {"id":"cs_old","object":"checkout.session","client_reference_id":"order-failed"}
                """);
        String latestFailure = event("evt_latest_failed", "checkout.session.async_payment_failed", """
                {"id":"cs_latest","object":"checkout.session","client_reference_id":"order-failed"}
                """);

        service.process(oldFailure, signature(oldFailure));
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);

        service.process(latestFailure, signature(latestFailure));
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void expirationUnlocksOnlyTheLatestAttempt() throws Exception {
        Payment payment = new Payment("order-expired", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_latest", null, null, "attempt-2");
        payments.save(payment);
        String oldExpired = event("evt_old_expired", "checkout.session.expired", """
                {"id":"cs_old","object":"checkout.session","client_reference_id":"order-expired","status":"expired"}
                """);
        String latestExpired = event("evt_latest_expired", "checkout.session.expired", """
                {"id":"cs_latest","object":"checkout.session","client_reference_id":"order-expired","status":"expired"}
                """);

        service.process(oldExpired, signature(oldExpired));
        assertThat(payments.findById(payment.getId()).orElseThrow().getCheckoutStatus()).isEqualTo(CheckoutStatus.OPEN);

        service.process(latestExpired, signature(latestExpired));
        Payment expired = payments.findById(payment.getId()).orElseThrow();
        assertThat(expired.getCheckoutStatus()).isEqualTo(CheckoutStatus.EXPIRED);
        assertThat(expired.canStartCheckoutAttempt()).isTrue();
    }

    @Test
    void rejectsInvalidSignatureBeforePersistingAnything() {
        String payload = event("evt_bad", "customer.created", "{\"id\":\"cus_1\",\"object\":\"customer\"}");

        assertThatThrownBy(() -> service.process(payload, "t=1,v1=invalid"))
                .isInstanceOf(SignatureVerificationException.class);
        assertThat(events.count()).isZero();
    }

    @Test
    void terminalDuplicateIsAcknowledgedWithoutReprocessing() throws Exception {
        String payload = event("evt_duplicate", "customer.created", "{\"id\":\"cus_1\",\"object\":\"customer\"}");

        WebhookResult first = service.process(payload, signature(payload));
        WebhookResult duplicate = service.process(payload, signature(payload));

        assertThat(first.status()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void retriesStrandedReceivedAndPreviouslyFailedEvents() throws Exception {
        events.save(new WebhookEvent("evt_received", "customer.created"));
        WebhookEvent failed = new WebhookEvent("evt_failed", "customer.created");
        failed.failed(new IllegalStateException("temporary failure"));
        events.save(failed);
        String receivedPayload = event("evt_received", "customer.created",
                "{\"id\":\"cus_1\",\"object\":\"customer\"}");
        String failedPayload = event("evt_failed", "customer.created",
                "{\"id\":\"cus_2\",\"object\":\"customer\"}");

        WebhookResult receivedRetry = service.process(receivedPayload, signature(receivedPayload));
        WebhookResult failedRetry = service.process(failedPayload, signature(failedPayload));

        assertThat(receivedRetry.status()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(failedRetry.status()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(receivedRetry.duplicate()).isTrue();
        assertThat(failedRetry.duplicate()).isTrue();
    }

    @Test
    void recordsVisibleFailureWhenSupportedPayloadCannotDeserialize() throws Exception {
        String payload = eventWithVersion("evt_mismatch", "checkout.session.completed",
                "{\"id\":\"cs_bad\",\"object\":\"checkout.session\"}", "2020-08-27");

        assertThatThrownBy(() -> service.process(payload, signature(payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned API version");
        WebhookEvent failed = events.findByStripeEventId("evt_mismatch").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("pinned API version");
    }

    @Test
    void processingFailureRollsBackBusinessWritesButKeepsFailedClaim() throws Exception {
        Payment payment = new Payment("order-over-refund", 100, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_over_refund", "pi_over_refund", null, "attempt-1");
        payment.markPaid("pi_over_refund", null);
        payments.save(payment);
        String payload = event("evt_over_refund", "refund.created", """
                {"id":"re_over","object":"refund","payment_intent":"pi_over_refund","amount":200,
                 "currency":"usd","status":"succeeded","metadata":{}}
                """);

        assertThatThrownBy(() -> service.process(payload, signature(payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the payment amount");

        assertThat(refunds.findByStripeRefundId("re_over")).isEmpty();
        Payment unchanged = payments.findById(payment.getId()).orElseThrow();
        assertThat(unchanged.getRefundedAmount()).isZero();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(events.findByStripeEventId("evt_over_refund").orElseThrow().getStatus())
                .isEqualTo(WebhookEventStatus.FAILED);
    }

    @Test
    void nearConcurrentDuplicateDeliveryCreatesOneDurableClaim() throws Exception {
        String payload = event("evt_concurrent", "customer.created",
                "{\"id\":\"cus_1\",\"object\":\"customer\"}");
        String signature = signature(payload);
        Callable<WebhookResult> delivery = () -> service.process(payload, signature);

        List<WebhookResult> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            results = executor.invokeAll(List.of(delivery, delivery)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();
        }

        assertThat(events.count()).isEqualTo(1);
        assertThat(results).allMatch(result -> result.status() == WebhookEventStatus.IGNORED);
        assertThat(results).anyMatch(WebhookResult::duplicate);
    }

    @Test
    void successfulRefundUpdatesTotalExactlyOnceAcrossCreatedAndUpdatedEvents() throws Exception {
        Payment payment = new Payment("order-refund", 2000, "usd", null);
        payment = payments.save(payment);
        payment.attachCheckout("cs_refund", "pi_refund", null, "attempt-1");
        payment.markPaid("pi_refund", null);
        payments.save(payment);
        String refundObject = """
                {"id":"re_success","object":"refund","payment_intent":"pi_refund","amount":500,
                 "currency":"usd","status":"succeeded","reason":"requested_by_customer","metadata":{}}
                """;
        String created = event("evt_refund_created", "refund.created", refundObject);
        String updated = event("evt_refund_updated", "refund.updated", refundObject);

        service.process(created, signature(created));
        service.process(updated, signature(updated));

        Payment result = payments.findById(payment.getId()).orElseThrow();
        assertThat(result.getRefundedAmount()).isEqualTo(500);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(refunds.count()).isEqualTo(1);
    }

    private String event(String id, String type, String object) {
        return eventWithVersion(id, type, object, API_VERSION);
    }

    private String eventWithVersion(String id, String type, String object, String apiVersion) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"livemode":false,
                 "type":"%s","data":{"object":%s}}
                """.formatted(id, apiVersion, System.currentTimeMillis() / 1000, type, object.trim());
    }

    private String signature(String payload) throws Exception {
        return Webhook.Signature.generateSignatureHeader(payload, SECRET);
    }
}
