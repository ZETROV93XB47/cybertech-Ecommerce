package com.novatech.cybertech.exceptions;

public record QuantityUpdated(int newQuantity) implements QuantityChangeResult {}
