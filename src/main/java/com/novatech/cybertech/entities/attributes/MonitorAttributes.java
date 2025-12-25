package com.novatech.cybertech.entities.attributes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;


public record MonitorAttributes(
        @NotBlank String resolution,
        @Min(60) Integer refreshRate
) {
}