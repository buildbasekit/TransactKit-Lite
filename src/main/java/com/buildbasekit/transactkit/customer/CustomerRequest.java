package com.buildbasekit.transactkit.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CustomerRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 255) String name,
        @Size(max = 500) String description,
        @Size(max = 50) Map<@Size(max = 40) String, @Size(max = 500) String> metadata) {
}
