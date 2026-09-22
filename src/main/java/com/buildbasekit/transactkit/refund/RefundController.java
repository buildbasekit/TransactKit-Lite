package com.buildbasekit.transactkit.refund;

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

import java.util.UUID;

@RestController
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/api/payments/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    RefundResponse create(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) throws StripeException {
        return refundService.create(id, request);
    }

    @GetMapping("/api/refunds/{id}")
    RefundResponse get(@PathVariable @NotBlank @Size(max = 255) String id) throws StripeException {
        return refundService.get(id);
    }
}
