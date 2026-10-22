package com.novatech.cybertech.dto.response.userevent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of POST /events/consume-events. {@code ignored} counts the events of an unknown product,
 * dropped without failing the rest of the batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventBatchResponseDto {
    private int accepted;
    private int ignored;
}
