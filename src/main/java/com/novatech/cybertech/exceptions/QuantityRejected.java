package com.novatech.cybertech.exceptions;

public record QuantityRejected(QuantityRejectionReason reason) implements QuantityChangeResult {}