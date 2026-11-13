package com.novatech.cybertech.dto.request.review;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDto {

    // Identity (userUuid) removed: identity is authoritatively derived from the JWT subject in the
    // controller/service layer. Allowing the client to declare a userUuid in the body created a
    // spoofing footgun if any future refactor accidentally read the DTO field instead of the JWT.

    @NotNull(message = "Order UUID cannot be null")
    private UUID orderUuid;

    @NotNull(message = "Product UUID cannot be null")
    private UUID productUuid;

    @NotNull(message = "Rating cannot be null")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment; // Le commentaire peut être optionnel
}
