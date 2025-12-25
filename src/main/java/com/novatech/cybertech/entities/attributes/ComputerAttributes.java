package com.novatech.cybertech.entities.attributes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ComputerAttributes(
        @NotBlank String cpu,
        @NotBlank String gpu,
        @Min(8) Integer ramGb
) {
}