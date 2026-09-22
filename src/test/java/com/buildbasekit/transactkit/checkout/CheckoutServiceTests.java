package com.buildbasekit.transactkit.checkout;

import com.buildbasekit.transactkit.config.StripeProperties;
import com.buildbasekit.transactkit.exception.ConflictException;
import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.buildbasekit.transactkit.payment.CheckoutStatus;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.payment.PaymentStatus;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTests {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripe;
    @Mock
    private ObjectProvider<StripeClient> stripeClients;
    @Mock
    private PaymentRepository payments;
    private CheckoutService service;

    @BeforeEach
    void setUp() {
        lenient().when(stripeClients.getIfAvailable()).thenReturn(stripe);
        StripeProperties properties = new StripeProperties("sk_test_unit", "whsec_unit", null,
                new StripeProperties.Checkout(URI.create("http://test/success"), URI.create("http://test/cancel")));
        service = new CheckoutService(stripeClients, properties, payments);
    }

    @Test
    void createsHostedPaymentCheckoutAndLocalPayment() throws Exception {
        Session session = session("cs_test_123", "open", "unpaid");
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stripe.v1().checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenReturn(session);

        CheckoutResponse response = service.create(request("order-1", "attempt-1", 2000));

        assertThat(response.checkoutSessionId()).isEqualTo("cs_test_123");
        assertThat(response.checkoutUrl()).startsWith("https://checkout.stripe.test");
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripe.v1().checkout().sessions()).create(params.capture(), options.capture());
        assertThat(params.getValue().getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
        assertThat(params.getValue().getSuccessUrl()).isEqualTo("http://test/success");
        assertThat(params.getValue().getCancelUrl()).isEqualTo("http://test/cancel");
        assertThat(params.getValue().getCustomerEmail()).isEqualTo("buyer@example.com");
        assertThat(params.getValue().getClientReferenceId()).isEqualTo("order-1");
        assertThat(params.getValue().getMetadata()).containsEntry("businessReference", "order-1");
        assertThat(params.getValue().getMetadata()).containsKey("paymentId");
        assertThat(params.getValue().getLineItems().getFirst().getPriceData().getUnitAmount()).isEqualTo(2000);
        assertThat(params.getValue().getLineItems().getFirst().getPriceData().getCurrency()).isEqualTo("usd");
        assertThat(options.getValue().getIdempotencyKey())
                .isEqualTo("checkout:" + response.paymentId() + ":attempt-1");
    }

    @Test
    void accidentalRetryReusesStillOpenSession() throws Exception {
        Payment payment = withId(new Payment("order-1", 2000, "usd", null));
        payment.attachCheckout("cs_open", "pi_open", null, "attempt-1");
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.of(payment));
        when(stripe.v1().checkout().sessions().retrieve("cs_open"))
                .thenReturn(session("cs_open", "open", "unpaid"));

        CheckoutResponse response = service.create(request("order-1", "attempt-1", 2000));

        assertThat(response.checkoutSessionId()).isEqualTo("cs_open");
        verify(stripe.v1().checkout().sessions(), never())
                .create(any(SessionCreateParams.class), any(RequestOptions.class));
    }

    @Test
    void expiredAttemptCanBeReplacedOnlyWithNewAttemptReference() throws Exception {
        Payment payment = withId(new Payment("order-1", 2000, "usd", null));
        payment.attachCheckout("cs_expired", "pi_old", null, "attempt-1");
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.of(payment));
        when(stripe.v1().checkout().sessions().retrieve("cs_expired"))
                .thenReturn(session("cs_expired", "expired", "unpaid"));
        when(stripe.v1().checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenReturn(session("cs_new", "open", "unpaid"));

        CheckoutResponse response = service.create(request("order-1", "attempt-2", 2000));

        assertThat(response.checkoutSessionId()).isEqualTo("cs_new");
        assertThat(payment.getCheckoutAttemptReference()).isEqualTo("attempt-2");
        assertThat(payment.getCheckoutStatus()).isEqualTo(CheckoutStatus.OPEN);
    }

    @Test
    void refusesSameAttemptReferenceAfterExpiration() throws Exception {
        Payment payment = withId(new Payment("order-1", 2000, "usd", null));
        payment.attachCheckout("cs_expired", null, null, "attempt-1");
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.of(payment));
        when(stripe.v1().checkout().sessions().retrieve("cs_expired"))
                .thenReturn(session("cs_expired", "expired", "unpaid"));

        assertThatThrownBy(() -> service.create(request("order-1", "attempt-1", 2000)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("new attemptReference");
    }

    @Test
    void rejectsBusinessReferenceWithDifferentAmountBeforeCreatingSession() {
        Payment existing = withId(new Payment("order-1", 1000, "usd", null));
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request("order-1", "attempt-1", 2000)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void paidPaymentCannotStartAnotherCheckout() {
        Payment existing = withId(new Payment("order-1", 2000, "usd", null));
        existing.markPaid("pi_paid", null);
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request("order-1", "attempt-2", 2000)))
                .isInstanceOf(ConflictException.class);
        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void propagatesStripeCheckoutApiFailure() throws Exception {
        ApiException failure = new ApiException("Stripe unavailable", "req_1", "api_error", 500, null);
        when(payments.findByBusinessReference("order-1")).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(stripe.v1().checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.create(request("order-1", "attempt-1", 2000))).isSameAs(failure);
    }

    @Test
    void failsClearlyWithoutStripeConfiguration() {
        ObjectProvider<StripeClient> unavailable = mock(ObjectProvider.class);
        StripeProperties properties = new StripeProperties("", "", null,
                new StripeProperties.Checkout(URI.create("http://test/success"), URI.create("http://test/cancel")));
        CheckoutService unconfigured = new CheckoutService(unavailable, properties, payments);

        assertThatThrownBy(() -> unconfigured.create(request("order-1", "attempt-1", 2000)))
                .isInstanceOf(StripeConfigurationException.class);
    }

    private CheckoutRequest request(String businessReference, String attemptReference, long amount) {
        return new CheckoutRequest(amount, "USD", "Order", businessReference, attemptReference,
                "buyer@example.com", null, Map.of("cart", "123"));
    }

    private Session session(String id, String status, String paymentStatus) {
        Session session = new Session();
        session.setId(id);
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setUrl("https://checkout.stripe.test/session");
        session.setPaymentIntent("pi_" + id);
        session.setCustomer("cus_test_123");
        return session;
    }

    private Payment withId(Payment payment) {
        try {
            java.lang.reflect.Field field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, UUID.fromString("00000000-0000-0000-0000-000000000001"));
            return payment;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
