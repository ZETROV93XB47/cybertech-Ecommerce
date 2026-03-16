package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.mappers.document.SpecificProductAttributes;

import java.util.Map;

public interface AttributesFactory {
    Map<String, SpecificProductAttributes> create(final Category category, final Map<String, Object> raw);
}
