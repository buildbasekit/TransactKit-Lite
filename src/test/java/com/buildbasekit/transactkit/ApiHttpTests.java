package com.buildbasekit.transactkit;

import com.stripe.net.Webhook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiHttpTests {

    @Autowired
    private MockMvc http;

    @Test
    void servesManualTestFrontendAndCheckoutResultPages() throws Exception {
        http.perform(get("/api-test/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create Checkout Session")));
        http.perform(get("/api-test/success.html")).andExpect(status().isOk());
        http.perform(get("/api-test/cancel.html")).andExpect(status().isOk());
    }

    @Test
    void returnsProblemDetailForMissingPayment() throws Exception {
        http.perform(get("/api/payments/{id}", UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void validatesCheckoutHttpRequest() throws Exception {
        http.perform(post("/api/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0,"currency":"US","description":"","businessReference":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void rejectsInvalidUuidPathVariable() throws Exception {
        http.perform(get("/api/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsBlankStripeIdentifierThroughMvcMethodValidation() throws Exception {
        http.perform(get(URI.create("/api/customers/%20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void rejectsInvalidWebhookSignatureAtHttpBoundary() throws Exception {
        http.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void rejectsMissingWebhookSignatureAtHttpBoundary() throws Exception {
        http.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSignedMalformedWebhookPayloadAtHttpBoundary() throws Exception {
        String payload = "not-json";
        String signature = Webhook.Signature.generateSignatureHeader(payload, "whsec_unit_secret");

        http.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Webhook payload is not valid Stripe event JSON."));
    }

    @Test
    void rejectsMalformedJsonRequestBody() throws Exception {
        http.perform(post("/api/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());
    }
}
