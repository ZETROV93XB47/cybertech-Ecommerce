package com.novatech.cybertech.dispatcher;

import com.novatech.cybertech.dto.data.ShippingContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.ShippingProviderStrategyFactory;
import com.novatech.cybertech.services.core.ShippingProviderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ShippingDispatcher}. Covers per-provider happy paths and the missing-strategy
 * branch for each {@link ShippingProvider}.
 */
@ExtendWith(MockitoExtension.class)
class ShippingDispatcherTest {

    @Mock
    private ShippingProviderStrategyFactory shippingProviderStrategyFactory;
    @Mock
    private ShippingProviderService shippingProviderService;

    @InjectMocks
    private ShippingDispatcher dispatcher;

    private ShippingContext contextFor(ShippingProvider provider) {
        return ShippingContext.builder()
                .packageId("pkg-1")
                .user(UserContactDto.builder()
                        .name("Jane").email("jane@example.com").phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL).build())
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(provider)
                .build();
    }

    @ParameterizedTest
    @EnumSource(ShippingProvider.class)
    void dispatchHappyPathShouldResolveAndDeliver(ShippingProvider provider) {
        ShippingContext context = contextFor(provider);
        when(shippingProviderStrategyFactory.getStrategy(provider)).thenReturn(shippingProviderService);
        when(shippingProviderService.deliver("pkg-1", ShippingType.STANDARD)).thenReturn("OK-" + provider.name());

        dispatcher.dispatch(context);

        verify(shippingProviderService).deliver(eq("pkg-1"), eq(ShippingType.STANDARD));
    }

    @ParameterizedTest
    @EnumSource(ShippingProvider.class)
    void dispatchShouldThrowWhenStrategyMissingForEachProvider(ShippingProvider provider) {
        ShippingContext context = contextFor(provider);
        when(shippingProviderStrategyFactory.getStrategy(provider)).thenReturn(null);

        assertThatThrownBy(() -> dispatcher.dispatch(context))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class)
                .hasMessageContaining(provider.name());
    }
}
