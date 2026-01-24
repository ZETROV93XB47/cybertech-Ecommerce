package com.novatech.cybertech.entities.attributes;

import com.novatech.cybertech.entities.enums.Brand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ComputerAttributes(
        @NotBlank String cpu,
        @NotBlank String gpu,
        @Min(8) Integer ram,
        @NotBlank String os,
        @NotBlank String connectivity,
        @NotBlank String displayType,
        @Min(128) Integer memory,
        @NotNull Brand brand
) {
}