package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.mappers.document.ComputerProductAttributes;
import com.novatech.cybertech.mappers.document.SpecificProductAttributes;
import com.novatech.cybertech.services.core.AttributesFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProductAttributesFactoryImp implements AttributesFactory {

    public static final String RAM_COMPUTER_ATTRIBUTE = "ram";
    public static final String MEMORY_COMPUTER_ATTRIBUTE = "memory";
    public static final String CPU = "cpu";
    public static final String CPU_COMPUTER_ATTRIBUTE = "cpu";
    public static final String GPU_COMPUTER_ATTRIBUTE = "gpu";
    public static final String OS_COMPUTER_ATTRIBUTE = "os";
    public static final String DISPLAY_TYPE_COMPUTER_ATTRIBUTE = "displayType";
    public static final String CONNECTIVITY_COMPUTER_ATTRIBUTE = "connectivity";

    public Map<String, SpecificProductAttributes> create(final Category category, final Map<String, Object> raw) {

        return switch (category) {
            case COMPUTER, MONITOR, SMARTPHONE, KEYBOARD ->
                    buildComputerAttributes(raw, category); //I'll implement later the other categories but this part is mostly to show that based on the productCategory, we can create the corresponding sub document object
            default -> null;
        };
    }

    private static Map<String, SpecificProductAttributes> buildComputerAttributes(final Map<String, Object> raw, final Category category) {

        final ComputerProductAttributes computerAttributesMap = ComputerProductAttributes.builder()
                .ram((Integer) raw.get(RAM_COMPUTER_ATTRIBUTE))
                .memory((Integer) raw.get(MEMORY_COMPUTER_ATTRIBUTE))
                .cpu((String) raw.get(CPU_COMPUTER_ATTRIBUTE))
                .gpu((String) raw.get(GPU_COMPUTER_ATTRIBUTE))
                .os((String) raw.get(OS_COMPUTER_ATTRIBUTE))
                .displayType((String) raw.get(DISPLAY_TYPE_COMPUTER_ATTRIBUTE))
                .connectivity((String) raw.get(CONNECTIVITY_COMPUTER_ATTRIBUTE))
                .build();

        return Map.of(category.toString(), computerAttributesMap);
    }
}
