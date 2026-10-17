package com.novatech.cybertech.dto.request.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RangeFilter {
    private Double min;
    private Double max;
}
