package com.buildbasekit.transactkit.checkout;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CheckoutRequest(
        @Positive long amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
        @NotBlank @Size(max = 255) String description,
        @NotBlank @Size(max = 100) String businessReference,
        @NotBlank @Size(max = 100) String attemptReference,
        @Email @Size(max = 254) String customerEmail,
        @Size(max = 255) String stripeCustomerId,
        @Size(max = 48) Map<@Size(max = 40) String, @Size(max = 500) String> metadata) {
}
