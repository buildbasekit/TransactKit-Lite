package com.buildbasekit.transactkit.config;

import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StripeConfig {

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${stripe.secret-key:}')")
    StripeClient stripeClient(StripeProperties properties) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setMaxNetworkRetries(2);
        if (properties.apiBase() != null) {
            builder.setApiBase(properties.apiBase().toString());
        }
        return builder.build();
    }
}
