package com.buildbasekit.transactkit.exception;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Request conflict", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, SignatureVerificationException.class})
    ProblemDetail badRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request fields are invalid.");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        return detail;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail methodValidation(HandlerMethodValidationException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more request parameters are invalid.");
        detail.setProperty("errors", exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error ->
                        result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .toList());
        return detail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                exception.getName() + " has an invalid value.");
    }

    @ExceptionHandler(StripeConfigurationException.class)
    ProblemDetail configuration(StripeConfigurationException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is not configured", exception.getMessage());
    }

    @ExceptionHandler(StripeException.class)
    ProblemDetail stripe(StripeException exception) {
        if (exception instanceof InvalidRequestException) {
            HttpStatus status = Integer.valueOf(404).equals(exception.getStatusCode())
                    ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return problem(status, status == HttpStatus.NOT_FOUND ? "Stripe resource not found" : "Stripe rejected the request",
                    safeStripeDetail(exception, "Stripe rejected one or more request values."));
        }
        if (exception instanceof PermissionException || exception instanceof AuthenticationException) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Stripe authentication failed",
                    "Stripe rejected the server's credentials or permissions.");
        }
        if (exception instanceof RateLimitException) {
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Stripe rate limit exceeded",
                    "Stripe temporarily rate-limited the request.");
        }
        if (exception instanceof ApiConnectionException) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Stripe is unavailable",
                    "The application could not connect to Stripe.");
        }
        if (exception instanceof ApiException || statusAtLeast(exception, 500)) {
            return problem(HttpStatus.BAD_GATEWAY, "Stripe request failed",
                    "Stripe could not complete the request.");
        }
        return problem(HttpStatus.BAD_GATEWAY, "Stripe request failed", "Stripe could not complete the request.");
    }

    private String safeStripeDetail(StripeException exception, String fallback) {
        return exception.getStripeError() != null && exception.getStripeError().getMessage() != null
                ? exception.getStripeError().getMessage() : fallback;
    }

    private boolean statusAtLeast(StripeException exception, int status) {
        return exception.getStatusCode() != null && exception.getStatusCode() >= status;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}
