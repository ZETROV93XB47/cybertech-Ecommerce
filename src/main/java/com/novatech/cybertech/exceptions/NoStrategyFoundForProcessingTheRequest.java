package com.novatech.cybertech.exceptions;

public class NoStrategyFoundForProcessingTheRequest extends RuntimeException {
    public NoStrategyFoundForProcessingTheRequest(String message) {
        super(message);
    }

    public NoStrategyFoundForProcessingTheRequest(String message, Throwable cause) {
        super(message, cause);
    }
}
