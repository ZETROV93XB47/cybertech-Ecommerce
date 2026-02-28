package com.novatech.cybertech.dto.response.moderation;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ModerationResponseDto implements Serializable {
    private String label;
    private float score;
    private boolean isHateful;
}
