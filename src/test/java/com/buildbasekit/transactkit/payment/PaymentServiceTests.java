package com.buildbasekit.transactkit.payment;

import com.buildbasekit.transactkit.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTests {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentService service = new PaymentService(payments);

    @Test
    void retrievesPayment() {
        Payment payment = new Payment("order-1", 2000, "usd", null);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        setId(payment, id);
        when(payments.findById(id)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.get(id);

        assertThat(response.businessReference()).isEqualTo("order-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reportsMissingPayment() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(payments.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    private void setId(Payment payment, UUID id) {
        try {
            java.lang.reflect.Field field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(payment, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
