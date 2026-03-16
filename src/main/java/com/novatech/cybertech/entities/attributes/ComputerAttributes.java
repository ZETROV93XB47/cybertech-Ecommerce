package com.novatech.cybertech.entities.attributes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

public record ComputerAttributes(
        @NotBlank @Getter @Setter String cpu,
        @NotBlank @Getter @Setter String gpu,
        @Getter @Setter @Min(8) Integer ram,
        @NotBlank @Getter @Setter String os,
        @NotBlank @Getter @Setter String connectivity,
        @NotBlank @Getter @Setter String displayType,
        @Getter @Setter @Min(32) Integer memory
) {
}