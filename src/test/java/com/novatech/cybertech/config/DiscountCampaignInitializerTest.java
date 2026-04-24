package com.novatech.cybertech.config;

import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountCampaignInitializerTest {

    @Mock
    private DiscountCampaignRepository discountCampaignRepository;

    @InjectMocks
    private DiscountCampaignInitializer initializer;

    @Test
    void seedsMissingTypes() throws Exception {
        when(discountCampaignRepository.findByDiscountType(any())).thenReturn(Optional.empty());
        when(discountCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        initializer.run(null);

        verify(discountCampaignRepository, times(DiscountType.values().length)).save(any());
    }

    @Test
    void skipsExistingTypes() throws Exception {
        final DiscountCampaignEntity existing = DiscountCampaignEntity.builder()
                .discountType(DiscountType.BLACK_FRIDAY)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .enabled(true)
                .build();
        when(discountCampaignRepository.findByDiscountType(any())).thenReturn(Optional.of(existing));

        initializer.run(null);

        verify(discountCampaignRepository, never()).save(any());
    }

    @Test
    void noDiscountSeededAsEnabled() throws Exception {
        when(discountCampaignRepository.findByDiscountType(any())).thenReturn(Optional.empty());
        when(discountCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        initializer.run(null);

        final ArgumentCaptor<DiscountCampaignEntity> captor = ArgumentCaptor.forClass(DiscountCampaignEntity.class);
        verify(discountCampaignRepository, atLeastOnce()).save(captor.capture());

        final DiscountCampaignEntity noDiscount = captor.getAllValues().stream()
                .filter(e -> e.getDiscountType() == DiscountType.NO_DISCOUNT)
                .findFirst()
                .orElseThrow();

        assertThat(noDiscount.isEnabled()).isTrue();
        assertThat(noDiscount.getCalculationType()).isEqualTo(DiscountCalculationType.NONE);
    }

    @Test
    void blackFridaySeededWithCorrectDefaults() throws Exception {
        when(discountCampaignRepository.findByDiscountType(any())).thenReturn(Optional.empty());
        when(discountCampaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        initializer.run(null);

        final ArgumentCaptor<DiscountCampaignEntity> captor = ArgumentCaptor.forClass(DiscountCampaignEntity.class);
        verify(discountCampaignRepository, atLeastOnce()).save(captor.capture());

        final DiscountCampaignEntity bf = captor.getAllValues().stream()
                .filter(e -> e.getDiscountType() == DiscountType.BLACK_FRIDAY)
                .findFirst()
                .orElseThrow();

        assertThat(bf.isEnabled()).isFalse();
        assertThat(bf.getCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(bf.getPercentage()).isEqualByComparingTo("20");
        assertThat(bf.getPriority()).isEqualTo(100);
    }
}
