package com.novatech.cybertech.dto.request.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_BATCH_MAX_SIZE;

/** Body of POST /events/consume-events: the browser signals of a page, sent together. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEventBatchRequestDto {

    @NotEmpty(message = "events must not be empty")
    @Size(max = USER_EVENT_BATCH_MAX_SIZE, message = "at most " + USER_EVENT_BATCH_MAX_SIZE + " events per call")
    private List<@Valid UserEventDto> events;
}
