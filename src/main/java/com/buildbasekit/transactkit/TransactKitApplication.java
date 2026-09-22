package com.buildbasekit.transactkit;

import com.buildbasekit.transactkit.config.StripeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StripeProperties.class)
public class TransactKitApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactKitApplication.class, args);
    }
}
