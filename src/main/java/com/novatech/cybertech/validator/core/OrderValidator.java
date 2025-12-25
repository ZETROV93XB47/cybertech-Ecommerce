package com.novatech.cybertech.validator.core;

import com.novatech.cybertech.dto.data.OrderValidationDto;

public interface OrderValidator {
    void validate(final OrderValidationDto entity);
}
