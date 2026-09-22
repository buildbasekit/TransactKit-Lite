package com.buildbasekit.transactkit.checkout;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidRequest() {
        CheckoutRequest request = new CheckoutRequest(2000, "usd", "Order", "order-1", "attempt-1",
                "buyer@example.com", null, Map.of("cart", "123"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsInvalidAmountCurrencyEmailAndReference() {
        CheckoutRequest request = new CheckoutRequest(0, "US", "", "", "", "bad", "not-a-customer", Map.of());

        assertThat(validator.validate(request)).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void rejectsOversizedMetadataMap() {
        Map<String, String> metadata = java.util.stream.IntStream.range(0, 49).boxed()
                .collect(java.util.stream.Collectors.toMap(i -> "key" + i, i -> "value"));
        CheckoutRequest request = new CheckoutRequest(2000, "usd", "Order", "order-1", "attempt-1",
                null, null, metadata);

        assertThat(validator.validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("metadata"));
    }
}
