package com.novatech.cybertech.dto.request.wishlist;

import lombok.Data;

import java.util.UUID;

@Data
public class WishlistUpdateRequestDto {
    private UUID uuid;
    // Généralement peu utilisé pour une wishlist, mais nécessaire pour le CrudBaseService
}