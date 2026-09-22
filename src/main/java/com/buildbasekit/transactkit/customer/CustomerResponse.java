package com.buildbasekit.transactkit.customer;

import com.stripe.model.Customer;

import java.util.Map;

public record CustomerResponse(String id, String email, String name, String description, Map<String, String> metadata) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getEmail(), customer.getName(), customer.getDescription(),
                customer.getMetadata());
    }
}
