package com.novatech.cybertech.dto.response.moderation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResponseDto implements Serializable {
    private String label;
    private Double score;
    //private boolean isHateful;
}
