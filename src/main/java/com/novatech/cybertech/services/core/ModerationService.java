package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;

public interface ModerationService {
    ModerationResponseDto checkIfIsHateful(final String comment);
}
