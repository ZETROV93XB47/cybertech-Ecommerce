package com.novatech.cybertech.dispatcher;

import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.ShippingProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingDispatcher {

    private final ShippingProviderStrategyFactory shippingProviderStrategyFactory;

    public void dispatch(final ShippingContext shippingContext) {
        final ShippingProviderService shippingProvider = shippingProviderStrategyFactory.getStrategy(shippingContext.getShippingProvider());

        if (shippingProvider == null) {
            log.error("Aucune stratégie trouvée pour ShippingType={} ou ShippingProvider={}", shippingContext.getShippingType(), shippingContext.getShippingProvider());
            throw new NoStrategyFoundForProcessingTheRequest("Aucune stratégie trouvée pour ShippingType=" + shippingContext.getShippingType() + " ou ShippingProvider = " + shippingContext.getShippingProvider());
        }

        final String deliveringMessage = shippingProvider.deliver(shippingContext.getPackageId(), shippingContext.getShippingType());
        log.info("Delivering shipping message {} ", deliveringMessage);
    }
}
