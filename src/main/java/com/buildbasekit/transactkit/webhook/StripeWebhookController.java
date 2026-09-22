package com.buildbasekit.transactkit.webhook;

import com.stripe.exception.SignatureVerificationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stripe/webhook")
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    public StripeWebhookController(StripeWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    WebhookResult receive(@RequestBody String rawBody,
                          @RequestHeader("Stripe-Signature") String signature) throws SignatureVerificationException {
        return webhookService.process(rawBody, signature);
    }
}
