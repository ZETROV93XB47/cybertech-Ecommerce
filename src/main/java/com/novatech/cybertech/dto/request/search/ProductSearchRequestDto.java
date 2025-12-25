package com.novatech.cybertech.dto.request.search;

import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.entities.enums.Os;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductSearchRequestDto {
    private String keyword;
    private Integer priceMin;
    private Integer priceMax;
    private List<Os> os;
    private List<String> ram;
    private List<String> ssd;
    private List<Brand> brand;
    private List<Category> category;
    private List<String> displaySize;
    private List<String> displayType;
}
