package com.novatech.cybertech.exceptions;

public class ProductAlreadyInWishlist extends RuntimeException {
    public ProductAlreadyInWishlist(String message) {
        super(message);
    }
}
