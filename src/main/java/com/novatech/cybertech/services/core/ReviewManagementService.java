package com.novatech.cybertech.services.core;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;

import java.util.UUID;

public interface ReviewManagementService {
    ReviewResponseDto getByUUID(final UUID uuid);

    ReviewResponseDto create(final ReviewCreateRequestDto reviewCreateRequestDto, final String keycloakId);

    ReviewResponseDto update(final ReviewUpdateRequestDto reviewUpdateRequestDto, final String keycloakId);

    void deleteByUUID(final UUID uuid, final String keycloakId);
}
