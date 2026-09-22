package com.buildbasekit.transactkit.refund;

import com.buildbasekit.transactkit.exception.ConflictException;
import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.payment.PaymentStatus;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceTests {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripe;
    @Mock
    private ObjectProvider<StripeClient> stripeClients;
    @Mock
    private PaymentRepository payments;
    @Mock
    private RefundRepository refunds;
    private RefundService service;

    @BeforeEach
    void setUp() {
        lenient().when(stripeClients.getIfAvailable()).thenReturn(stripe);
        service = new RefundService(stripeClients, payments, refunds);
    }

    @Test
    void createsFullRefund() throws Exception {
        Payment payment = paidPayment(2000);
        Refund stripeRefund = refund("re_full", 2000, "succeeded");
        stub(payment, stripeRefund);

        RefundResponse response = service.create(payment.getId(),
                new RefundRequest(null, RefundReason.REQUESTED_BY_CUSTOMER, "full-1", Map.of()));

        assertThat(response.amount()).isEqualTo(2000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void createsPartialRefund() throws Exception {
        Payment payment = paidPayment(2000);
        Refund stripeRefund = refund("re_partial", 500, "succeeded");
        stub(payment, stripeRefund);

        service.create(payment.getId(), new RefundRequest(500L, null, "partial-1", Map.of()));

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripe.v1().refunds()).create(params.capture(), options.capture());
        assertThat(payment.getRefundedAmount()).isEqualTo(500);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_test");
        assertThat(params.getValue().getAmount()).isEqualTo(500);
        assertThat(params.getValue().getMetadata()).isEqualTo(Map.of(
                "businessReference", "order-1", "refundReference", "partial-1"));
        assertThat(options.getValue().getIdempotencyKey())
                .isEqualTo("refund:" + payment.getId() + ":partial-1");
    }

    @Test
    void supportsMultiplePartialRefundsThroughFinalRefundedState() throws Exception {
        Payment payment = paidPayment(2000);
        List<RefundRecord> records = new ArrayList<>();
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.findByStripeRefundId(any())).thenReturn(Optional.empty());
        when(refunds.save(any(RefundRecord.class))).thenAnswer(invocation -> {
            RefundRecord record = invocation.getArgument(0);
            records.add(record);
            return record;
        });
        when(refunds.findByPaymentId(payment.getId())).thenAnswer(invocation -> List.copyOf(records));
        when(stripe.v1().refunds().create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenReturn(refund("re_first", 500, "succeeded"), refund("re_second", 1500, "succeeded"));

        service.create(payment.getId(), new RefundRequest(500L, null, "partial-1", Map.of()));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        service.create(payment.getId(), new RefundRequest(null, null, "partial-2", Map.of()));
        assertThat(payment.getRefundedAmount()).isEqualTo(2000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void rejectsAmountAboveRemainingBalance() {
        Payment payment = paidPayment(2000);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.create(payment.getId(),
                new RefundRequest(2500L, null, "too-much", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAlreadyFullyRefundedPayment() {
        Payment payment = paidPayment(2000);
        payment.synchronizeRefundedAmount(2000);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.create(payment.getId(),
                new RefundRequest(null, null, "again", Map.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void failsClearlyWithoutStripeConfiguration() {
        ObjectProvider<StripeClient> unavailable = mock(ObjectProvider.class);
        RefundService unconfigured = new RefundService(unavailable, payments, refunds);

        assertThatThrownBy(() -> unconfigured.create(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new RefundRequest(null, null, "refund-1", Map.of())))
                .isInstanceOf(StripeConfigurationException.class);
    }

    @Test
    void stripeRefundFailureLeavesLocalPaymentUnchanged() throws Exception {
        Payment payment = paidPayment(2000);
        ApiException failure = new ApiException("Stripe failed", "req_1", "api_error", 500, null);
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(stripe.v1().refunds().create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.create(payment.getId(),
                new RefundRequest(500L, null, "partial-1", Map.of()))).isSameAs(failure);
        assertThat(payment.getRefundedAmount()).isZero();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(refunds, never()).save(any(RefundRecord.class));
    }

    private void stub(Payment payment, Refund stripeRefund) throws Exception {
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refunds.findByStripeRefundId(any())).thenReturn(Optional.empty());
        when(refunds.save(any(RefundRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stripe.v1().refunds().create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenReturn(stripeRefund);
        RefundRecord record = new RefundRecord(stripeRefund.getId(), payment.getId(), stripeRefund.getAmount(),
                stripeRefund.getStatus(), stripeRefund.getReason());
        when(refunds.findByPaymentId(payment.getId())).thenReturn(List.of(record));
    }

    private Payment paidPayment(long amount) {
        Payment payment = new Payment("order-1", amount, "usd", null);
        try {
            java.lang.reflect.Field field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        payment.attachCheckout("cs_test", "pi_test", "cus_test", "attempt-1");
        payment.markPaid("pi_test", "cus_test");
        return payment;
    }

    private Refund refund(String id, long amount, String status) {
        Refund refund = new Refund();
        refund.setId(id);
        refund.setPaymentIntent("pi_test");
        refund.setAmount(amount);
        refund.setCurrency("usd");
        refund.setStatus(status);
        refund.setReason("requested_by_customer");
        refund.setMetadata(Map.of());
        return refund;
    }
}
