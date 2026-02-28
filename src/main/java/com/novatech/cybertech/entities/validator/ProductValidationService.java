package com.novatech.cybertech.entities.validator;

import com.novatech.cybertech.entities.attributes.ComputerAttributes;
import com.novatech.cybertech.entities.attributes.MonitorAttributes;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.exceptions.ProductConstraintsViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductValidationService {

    private final Validator validator;
    private final ObjectMapper objectMapper;

    public void validateAttributes(final Category category, final Map<String, Object> attributes) {

        final Object attributeDto = switch (category) {
            case COMPUTER -> objectMapper.convertValue(attributes, ComputerAttributes.class);
            case MONITOR -> objectMapper.convertValue(attributes, MonitorAttributes.class);

            default -> throw new IllegalArgumentException("Catégorie inconnue");
        };

        final Set<ConstraintViolation<Object>> violations = validator.validate(attributeDto);
        if (!violations.isEmpty()) {
            log.info("The product attributes arent matching the constraints criteria");
            log.info("Violations: {}", violations);
            throw new ProductConstraintsViolationException("The product attributes arent matching the constraints criteria", violations);
        }
    }
}
