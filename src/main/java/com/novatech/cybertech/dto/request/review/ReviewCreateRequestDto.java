package com.novatech.cybertech.dto.request.review;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDto {

    @NotNull(message = "User UUID cannot be null")
    private UUID userUuid;

    @NotNull(message = "Product UUID cannot be null")
    private UUID productUuid;

    @NotNull(message = "Rating cannot be null")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating;

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment; // Le commentaire peut être optionnel
}
