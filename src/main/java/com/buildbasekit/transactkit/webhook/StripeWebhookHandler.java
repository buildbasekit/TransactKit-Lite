package com.buildbasekit.transactkit.webhook;

import com.buildbasekit.transactkit.payment.Payment;
import com.buildbasekit.transactkit.payment.PaymentRepository;
import com.buildbasekit.transactkit.refund.RefundService;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
class StripeWebhookHandler {

    private final WebhookEventService eventService;
    private final WebhookEventRepository events;
    private final PaymentRepository payments;
    private final RefundService refundService;

    StripeWebhookHandler(WebhookEventService eventService, WebhookEventRepository events,
                         PaymentRepository payments, RefundService refundService) {
        this.eventService = eventService;
        this.events = events;
        this.payments = payments;
        this.refundService = refundService;
    }

    @Transactional
    WebhookResult handle(Event stripeEvent) {
        boolean duplicate = false;
        try {
            eventService.claim(stripeEvent.getId(), stripeEvent.getType());
        } catch (DataIntegrityViolationException exception) {
            duplicate = true;
        }

        WebhookEvent localEvent = events.findByStripeEventIdForUpdate(stripeEvent.getId())
                .orElseThrow(() -> new IllegalStateException("Webhook event claim was not persisted."));
        if (localEvent.getStatus() == WebhookEventStatus.PROCESSED
                || localEvent.getStatus() == WebhookEventStatus.IGNORED) {
            return result(localEvent, true);
        }

        if (dispatch(stripeEvent)) {
            localEvent.processed();
        } else {
            localEvent.ignored();
        }
        return result(localEvent, duplicate);
    }

    private boolean dispatch(Event event) {
        return switch (event.getType()) {
            case "checkout.session.completed" -> {
                synchronizeCompleted(requireObject(event, Session.class));
                yield true;
            }
            case "checkout.session.async_payment_succeeded" -> {
                synchronizeSucceeded(requireObject(event, Session.class));
                yield true;
            }
            case "checkout.session.async_payment_failed" -> {
                synchronizeFailed(requireObject(event, Session.class));
                yield true;
            }
            case "checkout.session.expired" -> {
                synchronizeExpired(requireObject(event, Session.class));
                yield true;
            }
            case "refund.created", "refund.updated", "refund.failed" -> {
                refundService.synchronize(requireObject(event, Refund.class));
                yield true;
            }
            default -> false;
        };
    }

    private void synchronizeCompleted(Session session) {
        findPayment(session).ifPresent(payment -> {
            if ("paid".equals(session.getPaymentStatus())) {
                payment.markPaid(session.getPaymentIntent(), session.getCustomer());
            } else {
                payment.markCheckoutProcessing(session.getId(), session.getPaymentIntent(), session.getCustomer());
            }
        });
    }

    private void synchronizeSucceeded(Session session) {
        findPayment(session).ifPresent(payment -> payment.markPaid(session.getPaymentIntent(), session.getCustomer()));
    }

    private void synchronizeFailed(Session session) {
        payments.findByStripeCheckoutSessionId(session.getId())
                .ifPresent(payment -> payment.markCheckoutFailed(session.getId()));
    }

    private void synchronizeExpired(Session session) {
        payments.findByStripeCheckoutSessionId(session.getId())
                .ifPresent(payment -> payment.markCheckoutExpired(session.getId()));
    }

    private Optional<Payment> findPayment(Session session) {
        Optional<Payment> payment = payments.findByStripeCheckoutSessionId(session.getId());
        if (payment.isEmpty() && session.getClientReferenceId() != null) {
            payment = payments.findByBusinessReference(session.getClientReferenceId());
        }
        return payment;
    }

    private <T extends StripeObject> T requireObject(Event event, Class<T> type) {
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new IllegalStateException(
                        "Stripe event data is incompatible with stripe-java's pinned API version."));
        if (!type.isInstance(object)) {
            throw new IllegalStateException("Stripe event contained an unexpected object type.");
        }
        return type.cast(object);
    }

    private WebhookResult result(WebhookEvent event, boolean duplicate) {
        return new WebhookResult(event.getStripeEventId(), event.getEventType(), event.getStatus(), duplicate);
    }
}
