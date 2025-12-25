package com.novatech.cybertech.dto.request.product;

import com.novatech.cybertech.entities.enums.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequestDto {

    @NotNull(message = "Product UUID cannot be null")
    private UUID productUuid;

    @Size(min = 3, max = 255, message = "Product name must be between 3 and 255 characters")
    private String name;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    private Brand brand;

    private Category category;

    private String cpu;

    @Size(max = 50, message = "GPU information must be at most 50 characters")
    private String gpu;

    private Ram ram;

    private SSD ssd;

    private DisplayType displayType;

    private DisplaySize displaySize;

    private Os os;

    @Size(max = 255, message = "Connectivity details must be at most 255 characters")
    private String connectivity;

    @Size(max = 255, message = "Photo URL/path must be at most 255 characters")
    private String photo;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock; // Utiliser Integer pour permettre la nullité (non mise à jour)

    private String description;
}