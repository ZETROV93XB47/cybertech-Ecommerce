package com.novatech.cybertech.dto.request.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequestDto {

    @NotNull(message = "Review UUID cannot be null")
    private UUID reviewUuid;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating; // Optionnel, si non fourni, on ne met pas à jour

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment; // Optionnel, si non fourni, on ne met pas à jour
}
