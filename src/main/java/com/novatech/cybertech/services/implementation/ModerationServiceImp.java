package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.clients.CommentModerationClient;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.services.core.ModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationServiceImp implements ModerationService {

    private final CommentModerationClient commentModerationClient;

    @Override
    public ModerationResponseDto checkIfIsHateful(String comment) {
        return commentModerationClient.moderate(comment);
    }
}
