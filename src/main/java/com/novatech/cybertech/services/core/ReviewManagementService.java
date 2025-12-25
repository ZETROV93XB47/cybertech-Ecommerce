package com.novatech.cybertech.services.core;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;

import java.util.UUID;

public interface ReviewManagementService extends CrudBaseService<UUID, ReviewCreateRequestDto, ReviewUpdateRequestDto, ReviewResponseDto> {
}
