package com.novatech.cybertech.dto.response.review;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseDto {

    private UUID uuid;
    private UUID userUuid;
    private UUID productUuid;
    private String productName;
    private Integer rating;
    private String comment;
}