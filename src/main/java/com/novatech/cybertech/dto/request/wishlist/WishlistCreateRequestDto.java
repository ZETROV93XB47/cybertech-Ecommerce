package com.novatech.cybertech.dto.request.wishlist;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WishlistCreateRequestDto {
    @NotNull
    private UUID userUuid;
    @NotNull
    private UUID productUuid;
}