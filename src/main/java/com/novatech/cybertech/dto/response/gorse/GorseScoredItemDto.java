package com.novatech.cybertech.dto.response.gorse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape returned by Gorse's {@code GET /api/latest} (and other non-personalized) endpoints
 * (https://gorse.io/docs/api/restful-api.html) — an array of {@code {Id, Score}} objects, unlike
 * {@code GET /api/recommend/{user-id}} which returns a plain array of item id strings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GorseScoredItemDto {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Score")
    private double score;
}
