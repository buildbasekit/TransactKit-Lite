package com.buildbasekit.transactkit.checkout;

import java.util.UUID;

public record CheckoutResponse(UUID paymentId, String checkoutSessionId, String checkoutUrl) {
}
