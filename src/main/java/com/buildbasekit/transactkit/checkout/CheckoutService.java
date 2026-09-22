package com.buildbasekit.transactkit.checkout;

import com.buildbasekit.transactkit.config.StripeProperties;
import com.buildbasekit.transactkit.exception.ConflictException;
import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class CheckoutService {

    private final ObjectProvider<StripeClient> stripeClients;
    private final StripeProperties properties;
    private final PaymentRepository payments;

    public CheckoutService(ObjectProvider<StripeClient> stripeClients, StripeProperties properties,
                           PaymentRepository payments) {
        this.stripeClients = stripeClients;
        this.properties = properties;
        this.payments = payments;
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public CheckoutResponse create(CheckoutRequest request) throws StripeException {
        StripeClient stripe = requireStripe();
        Payment payment = payments.findByBusinessReference(request.businessReference())
                .orElseGet(() -> payments.saveAndFlush(new Payment(request.businessReference(), request.amount(),
                        request.currency().toLowerCase(Locale.ROOT), blankToNull(request.stripeCustomerId()))));

        if (payment.getAmount() != request.amount()
                || !payment.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new ConflictException("The business reference is already used by a different payment.");
        }
        if (payment.isSettled()) {
            throw new ConflictException("A paid or refunded payment cannot start another Checkout attempt.");
        }

        if (payment.getStripeCheckoutSessionId() != null) {
            Session existing = stripe.v1().checkout().sessions().retrieve(payment.getStripeCheckoutSessionId());
            synchronizeExistingSession(payment, existing);
            payments.save(payment);
            if ("open".equals(existing.getStatus())) {
                return new CheckoutResponse(payment.getId(), existing.getId(), existing.getUrl());
            }
            if (payment.isSettled()) {
                throw new ConflictException("The payment has already been paid.");
            }
            if (!payment.canStartCheckoutAttempt()) {
                throw new ConflictException("The latest Checkout attempt is still processing.");
            }
            if (request.attemptReference().equals(payment.getCheckoutAttemptReference())) {
                throw new ConflictException("Use a new attemptReference for a new Checkout attempt.");
            }
        }

        Map<String, String> metadata = new HashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.put("businessReference", request.businessReference());
        metadata.put("paymentId", payment.getId().toString());

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(properties.checkout().successUrl().toString())
                .setCancelUrl(properties.checkout().cancelUrl().toString())
                .setClientReferenceId(request.businessReference())
                .putAllMetadata(metadata)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().putAllMetadata(metadata).build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.currency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(request.amount())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.description())
                                        .build())
                                .build())
                        .build());

        String customerId = blankToNull(request.stripeCustomerId());
        String email = blankToNull(request.customerEmail());
        if (customerId != null) {
            builder.setCustomer(customerId);
        } else if (email != null) {
            builder.setCustomerEmail(email);
        }

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("checkout:" + payment.getId() + ":" + request.attemptReference())
                .build();
        Session session = stripe.v1().checkout().sessions().create(builder.build(), options);
        payment.attachCheckout(session.getId(), session.getPaymentIntent(), session.getCustomer(),
                request.attemptReference());
        payments.save(payment);
        return new CheckoutResponse(payment.getId(), session.getId(), session.getUrl());
    }

    private void synchronizeExistingSession(Payment payment, Session session) {
        if ("paid".equals(session.getPaymentStatus())) {
            payment.markPaid(session.getPaymentIntent(), session.getCustomer());
        } else if ("expired".equals(session.getStatus())) {
            payment.markCheckoutExpired(session.getId());
        } else if ("complete".equals(session.getStatus())) {
            payment.markCheckoutProcessing(session.getId(), session.getPaymentIntent(), session.getCustomer());
        }
    }

    private StripeClient requireStripe() {
        StripeClient stripe = stripeClients.getIfAvailable();
        if (stripe == null) {
            throw new StripeConfigurationException("Set STRIPE_SECRET_KEY before calling Stripe APIs.");
        }
        return stripe;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
