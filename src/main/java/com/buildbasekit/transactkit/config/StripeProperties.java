package com.buildbasekit.transactkit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

@ConfigurationProperties("stripe")
public record StripeProperties(String secretKey, String webhookSecret, URI apiBase, Checkout checkout) {

    public boolean hasWebhookSecret() {
        return StringUtils.hasText(webhookSecret);
    }

    public record Checkout(URI successUrl, URI cancelUrl) {
    }
}
