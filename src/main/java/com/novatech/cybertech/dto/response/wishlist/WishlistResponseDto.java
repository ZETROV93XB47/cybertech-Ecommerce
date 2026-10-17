package com.novatech.cybertech.dto.response.wishlist;

import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WishlistResponseDto {
    private UUID uuid;
    private ProductResponseDto product;
    private LocalDateTime addedAt;
}