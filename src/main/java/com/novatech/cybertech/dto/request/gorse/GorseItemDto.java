package com.novatech.cybertech.dto.request.gorse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Shape expected by Gorse's {@code POST /api/items} batch-insert endpoint
 * (https://gorse.io/docs/api/restful-api.html) — field names are PascalCase on the wire, hence
 * the explicit {@link JsonProperty} on every field. Labels carry category/brand so Gorse's
 * item-based similarity has something to lean on for cold-start products with no feedback yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GorseItemDto {

    @JsonProperty("ItemId")
    private String itemId;

    @JsonProperty("IsHidden")
    private boolean isHidden;

    @JsonProperty("Labels")
    private List<String> labels;

    @JsonProperty("Timestamp")
    private Instant timestamp;
}
