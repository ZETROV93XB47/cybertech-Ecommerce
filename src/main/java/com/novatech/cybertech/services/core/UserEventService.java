package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;

public interface UserEventService {
    UserEvent processEvent(UserEventDto userEventDto);
}