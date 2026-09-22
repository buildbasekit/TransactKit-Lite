package com.buildbasekit.transactkit.customer;

import com.buildbasekit.transactkit.exception.StripeConfigurationException;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTests {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripe;
    @Mock
    private ObjectProvider<StripeClient> stripeClients;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        lenient().when(stripeClients.getIfAvailable()).thenReturn(stripe);
        service = new CustomerService(stripeClients);
    }

    @Test
    void createsCustomer() throws Exception {
        Customer customer = customer();
        when(stripe.v1().customers().create(any(CustomerCreateParams.class)))
                .thenReturn(customer);

        CustomerResponse response = service.create(new CustomerRequest("buyer@example.com", "Buyer", null,
                Map.of("account", "42")));

        ArgumentCaptor<CustomerCreateParams> params = ArgumentCaptor.forClass(CustomerCreateParams.class);
        verify(stripe.v1().customers()).create(params.capture());
        assertThat(response.id()).isEqualTo("cus_test_123");
        assertThat(response.email()).isEqualTo("buyer@example.com");
        assertThat(params.getValue().getEmail()).isEqualTo("buyer@example.com");
        assertThat(params.getValue().getName()).isEqualTo("Buyer");
        assertThat(params.getValue().getDescription()).isNull();
        assertThat(params.getValue().getMetadata()).isEqualTo(Map.of("account", "42"));
    }

    @Test
    void retrievesCustomer() throws Exception {
        when(stripe.v1().customers().retrieve("cus_test_123")).thenReturn(customer());

        assertThat(service.get("cus_test_123").name()).isEqualTo("Buyer");
    }

    @Test
    void propagatesMissingCustomerFromOfficialStripeSdk() throws Exception {
        InvalidRequestException missing = new InvalidRequestException(
                "No such customer", "id", "req_1", "resource_missing", 404, null);
        when(stripe.v1().customers().retrieve("missing")).thenThrow(missing);

        assertThatThrownBy(() -> service.get("missing")).isSameAs(missing);
    }

    @Test
    void propagatesStripeBadRequest() throws Exception {
        InvalidRequestException invalid = new InvalidRequestException(
                "Invalid email", "email", "req_1", "parameter_invalid", 400, null);
        when(stripe.v1().customers().create(any(CustomerCreateParams.class))).thenThrow(invalid);

        assertThatThrownBy(() -> service.create(new CustomerRequest(
                "buyer@example.com", null, null, Map.of()))).isSameAs(invalid);
    }

    @Test
    void failsClearlyWhenStripeIsNotConfigured() {
        ObjectProvider<StripeClient> unavailable = mock(ObjectProvider.class);
        CustomerService unconfigured = new CustomerService(unavailable);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> unconfigured.create(
                new CustomerRequest("buyer@example.com", null, null, Map.of()))))
                .isInstanceOf(StripeConfigurationException.class);
    }

    private Customer customer() {
        Customer customer = new Customer();
        customer.setId("cus_test_123");
        customer.setEmail("buyer@example.com");
        customer.setName("Buyer");
        customer.setMetadata(Map.of("account", "42"));
        return customer;
    }
}
