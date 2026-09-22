package com.buildbasekit.transactkit.customer;

import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse create(@Valid @RequestBody CustomerRequest request) throws StripeException {
        return customerService.create(request);
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable @NotBlank @Size(max = 255) String id) throws StripeException {
        return customerService.get(id);
    }
}
