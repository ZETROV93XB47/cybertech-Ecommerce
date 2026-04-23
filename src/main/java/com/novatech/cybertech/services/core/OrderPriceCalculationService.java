package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.order.PriceCalculationRequestDto;
import com.novatech.cybertech.dto.response.order.PriceCalculationResultDto;

public interface OrderPriceCalculationService {

    PriceCalculationResultDto calculate(PriceCalculationRequestDto request);
}
