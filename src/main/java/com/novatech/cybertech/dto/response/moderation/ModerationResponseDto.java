package com.novatech.cybertech.dto.response.moderation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModerationResponseDto {
    private String label;
    private float score;
    private boolean isHateful;
}
