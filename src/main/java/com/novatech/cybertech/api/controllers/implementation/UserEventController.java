package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.UserEventControllerApiSpec;
import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.services.core.UserEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.USER_EVENT_INGESTION_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Behavioural-analytics ingestion HTTP surface. Front-end emits low-volume click / view events to be
 * persisted in MongoDB by {@link UserEventService}; the events feed the recommendation training
 * pipeline. Authentication is required so the calling user identity can be attached.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = USER_EVENT_INGESTION_BASE_PATH)
public class UserEventController implements UserEventControllerApiSpec {

    private final UserEventService userEventService;

    /**
     * POST /consume-event — accepts a behavioural event for the calling user.
     *
     * @param eventDto the inbound event payload
     * @param jwt      the caller identity
     * @return 201 with the persisted event document
     */
    @Override
    @PreAuthorize("hasRole('USER')")
    @PostMapping(value = "/consume-event", produces = APPLICATION_JSON_VALUE, consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<UserEvent> collectEvent(@Valid @RequestBody UserEventDto eventDto, @AuthenticationPrincipal Jwt jwt) {

        log.info("Received event for user {} : ", jwt.getSubject());

        // BUG-SPOOF-D5: never trust the client-supplied userId — overwrite with the JWT subject
        // so events are always attributed to the calling user (and stop attribution spoofing).
        eventDto.setUserId(jwt.getSubject());

        return ResponseEntity.status(HttpStatus.CREATED).body(userEventService.processEvent(eventDto));
    }
}