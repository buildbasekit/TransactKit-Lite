package com.buildbasekit.transactkit.exception;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvalidStripeInputToBadRequest() {
        ProblemDetail detail = handler.stripe(new InvalidRequestException(
                "invalid customer", "customer", "req_1", "parameter_invalid", 400, null));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getTitle()).isEqualTo("Stripe rejected the request");
    }

    @Test
    void mapsMissingStripeObjectToNotFound() {
        ProblemDetail detail = handler.stripe(new InvalidRequestException(
                "No such customer", "customer", "req_1", "resource_missing", 404, null));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void mapsStripeAuthenticationFailureToServiceUnavailableWithoutLeakingMessage() {
        ProblemDetail detail = handler.stripe(new AuthenticationException(
                "secret credential detail", "req_1", "api_key_expired", 401));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(detail.getDetail()).doesNotContain("secret credential detail");
    }

    @Test
    void mapsStripeRateLimitToTooManyRequests() {
        ProblemDetail detail = handler.stripe(new RateLimitException(
                "limited", null, "req_1", "rate_limit", 429, null));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void mapsStripeConnectionFailureToServiceUnavailable() {
        ProblemDetail detail = handler.stripe(new ApiConnectionException("offline"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void mapsStripeServerFailureToBadGateway() {
        ProblemDetail detail = handler.stripe(new ApiException("failed", "req_1", "api_error", 500, null));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }
}
