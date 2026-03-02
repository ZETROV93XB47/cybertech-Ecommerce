package com.novatech.cybertech.exceptions;

import com.stripe.exception.StripeException;

public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message, StripeException e) {
        super(message);
    }
}
