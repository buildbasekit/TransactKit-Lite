package com.buildbasekit.transactkit.customer;

import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final ObjectProvider<StripeClient> stripeClients;

    public CustomerService(ObjectProvider<StripeClient> stripeClients) {
        this.stripeClients = stripeClients;
    }

    public CustomerResponse create(CustomerRequest request) throws StripeException {
        StripeClient stripe = requireStripe();
        CustomerCreateParams.Builder builder = CustomerCreateParams.builder().setEmail(request.email());
        if (request.name() != null && !request.name().isBlank()) {
            builder.setName(request.name());
        }
        if (request.description() != null && !request.description().isBlank()) {
            builder.setDescription(request.description());
        }
        if (request.metadata() != null) {
            builder.putAllMetadata(request.metadata());
        }
        return CustomerResponse.from(stripe.v1().customers().create(builder.build()));
    }

    public CustomerResponse get(String id) throws StripeException {
        StripeClient stripe = requireStripe();
        return CustomerResponse.from(stripe.v1().customers().retrieve(id));
    }

    private StripeClient requireStripe() {
        StripeClient stripe = stripeClients.getIfAvailable();
        if (stripe == null) {
            throw new StripeConfigurationException("Set STRIPE_SECRET_KEY before calling Stripe APIs.");
        }
        return stripe;
    }
}
