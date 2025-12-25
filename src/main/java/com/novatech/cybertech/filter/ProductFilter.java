package com.novatech.cybertech.filter;

import com.novatech.cybertech.entities.enums.*;
import com.novatech.cybertech.filter.enums.FilterCriteria;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ProductFilter {

    private UUID productUuid;
    private Brand brand;
    private String name;
    private Category category;
    private DisplayType displayType;
    private Map<FilterCriteria, SSD> ssdMap;
    private Map<FilterCriteria, Ram> ramMap;
    private Map<FilterCriteria, Integer> stockMap;
    private Map<FilterCriteria, BigDecimal> priceMap;
    private Map<FilterCriteria, DisplaySize> displaySizeMap;

}