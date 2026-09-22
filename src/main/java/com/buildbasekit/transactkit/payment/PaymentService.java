package com.buildbasekit.transactkit.payment;

import com.buildbasekit.transactkit.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID id) {
        return PaymentResponse.from(require(id));
    }

    public Payment require(UUID id) {
        return payments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment " + id + " was not found."));
    }
}
