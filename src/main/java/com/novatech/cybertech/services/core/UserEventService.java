package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.dto.request.event.UserEventBatchRequestDto;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.dto.response.userevent.UserEventBatchResponseDto;
import com.novatech.cybertech.entities.document.UserEvent;

import java.util.List;

public interface UserEventService {

    /**
     * Stores one browser signal of the calling user ({@code userEventDto.userId}).
     *
     * @throws com.novatech.cybertech.exceptions.UserEventTypeNotAcceptedException for a type the backend produces itself
     * @throws com.novatech.cybertech.exceptions.ProductNotFoundException          for an unknown product
     */
    UserEvent processEvent(UserEventDto userEventDto);

    /**
     * Stores the browser signals of one page for {@code userId}, all-or-nothing on the event types;
     * the events of an unknown product are ignored (counted in the response) instead of failing the batch.
     */
    UserEventBatchResponseDto processEvents(UserEventBatchRequestDto batch, String userId);

    /**
     * Writes the signals produced by a committed business action. Never throws: a MongoDB failure
     * is logged at WARN and the signal is lost. A PURCHASE already stored for the same
     * (orderUuid, productUuid) is skipped.
     */
    void recordServerSignals(List<RecommendationSignalDto> signals);
}
