package com.novatech.cybertech.dto.request.product;

import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
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

    @NotNull(message = "Brand cannot be null")
    private Brand brand;

    @NotNull(message = "Category cannot be null")
    private Category category;

    @Size(max = 255, message = "Photo URL/path must be at most 255 characters")
    private String photo;

    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock; // Utiliser Integer pour permettre la nullité (non mise à jour)

    @NotNull(message = "description cannot be null")
    private String description;
}