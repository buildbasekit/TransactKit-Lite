package com.buildbasekit.transactkit.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuration")
public class ConfigurationController {

    private final ObjectProvider<StripeClient> stripeClients;
    private final StripeProperties properties;

    public ConfigurationController(ObjectProvider<StripeClient> stripeClients, StripeProperties properties) {
        this.stripeClients = stripeClients;
        this.properties = properties;
    }

    @GetMapping
    ConfigurationStatus status() {
        return new ConfigurationStatus(stripeClients.getIfAvailable() != null, properties.hasWebhookSecret());
    }
}
