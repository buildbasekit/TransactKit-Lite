package com.buildbasekit.transactkit.refund;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RefundRequest(
        @Positive Long amount,
        RefundReason reason,
        @NotBlank @Size(max = 100) String reference,
        @Size(max = 48) Map<@Size(max = 40) String, @Size(max = 500) String> metadata) {
}
