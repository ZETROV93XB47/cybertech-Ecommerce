package com.novatech.cybertech.exceptions;

public class OrderSummuryReportJobFailedException extends RuntimeException {
    public OrderSummuryReportJobFailedException(String message) {
        super(message);
    }

    public OrderSummuryReportJobFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
