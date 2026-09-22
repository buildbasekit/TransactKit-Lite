package com.buildbasekit.transactkit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "stripe.secret-key=",
        "stripe.webhook-secret=",
        "spring.datasource.url=jdbc:h2:mem:transactkit_no_credentials;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class NoStripeConfigurationTests {

    @Autowired
    private MockMvc http;

    @Test
    void contextStartsAndConfigurationEndpointExposesOnlyBooleans() throws Exception {
        http.perform(get("/api/configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stripeApiConfigured").value(false))
                .andExpect(jsonPath("$.webhookConfigured").value(false));
    }

    @Test
    void customerAndCheckoutFailImmediatelyWithControlledProblemDetails() throws Exception {
        http.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"buyer@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Stripe is not configured"));

        http.perform(post("/api/checkout/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2000,"currency":"usd","description":"Order",
                                 "businessReference":"order-1","attemptReference":"attempt-1"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Stripe is not configured"));
    }

    @Test
    void webhookWithoutSecretFailsAsConfigurationError() throws Exception {
        http.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Stripe is not configured"));
    }
}
