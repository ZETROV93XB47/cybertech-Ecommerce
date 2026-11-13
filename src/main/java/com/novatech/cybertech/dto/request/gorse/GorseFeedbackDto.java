package com.novatech.cybertech.dto.request.gorse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Shape expected by Gorse's feedback batch-insert endpoint
 * (https://gorse.io/docs/api/restful-api.html) — field names are PascalCase on the wire. Sent via
 * {@code PUT /api/feedback} (overwrite semantics), not {@code POST} (accumulate semantics) — see
 * {@link com.novatech.cybertech.clients.GorseClient#upsertFeedback} javadoc for why. {@code
 * feedbackType} carries the {@link com.novatech.cybertech.entities.enums.UserEventType} name
 * verbatim (PURCHASE, VIEW, RATING_NEGATIVE, ...) — Gorse feedback types are free-form strings,
 * so reusing the existing enum avoids a second taxonomy to keep in sync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GorseFeedbackDto {

    @JsonProperty("FeedbackType")
    private String feedbackType;

    @JsonProperty("UserId")
    private String userId;

    @JsonProperty("ItemId")
    private String itemId;

    @JsonProperty("Timestamp")
    private Instant timestamp;
}
