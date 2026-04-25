package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountCampaignServiceImpTest {

    @Mock
    private DiscountCampaignRepository discountCampaignRepository;

    @InjectMocks
    private DiscountCampaignServiceImp service;

    private DiscountCampaignEntity enabledPercentageCampaign(final DiscountType type) {
        return DiscountCampaignEntity.builder()
                .discountType(type)
                .calculationType(DiscountCalculationType.PERCENTAGE)
                .enabled(true)
                .percentage(BigDecimal.valueOf(20))
                .build();
    }

    @Test
    void happyPath_returnsDiscountContext() {
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(Optional.of(enabledPercentageCampaign(DiscountType.BLACK_FRIDAY)));

        final DiscountContext ctx = service.getActiveDiscountContext(DiscountType.BLACK_FRIDAY);

        assertThat(ctx.discountType()).isEqualTo(DiscountType.BLACK_FRIDAY);
        assertThat(ctx.calculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(ctx.percentage()).isEqualByComparingTo("20");
    }

    @Test
    void notFound_throwsDiscountTypeNotActiveException() {
        when(discountCampaignRepository.findByDiscountType(DiscountType.WINTER_SALES))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveDiscountContext(DiscountType.WINTER_SALES))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void disabled_throwsDiscountTypeNotActiveException() {
        final DiscountCampaignEntity campaign = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        campaign.setEnabled(false);
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.getActiveDiscountContext(DiscountType.BLACK_FRIDAY))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("is disabled");
    }

    @Test
    void startDateInFuture_throwsDiscountTypeNotActiveException() {
        final DiscountCampaignEntity campaign = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        campaign.setStartsAt(LocalDateTime.now().plusDays(1));
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.getActiveDiscountContext(DiscountType.BLACK_FRIDAY))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("has not started yet");
    }

    @Test
    void endDateInPast_throwsDiscountTypeNotActiveException() {
        final DiscountCampaignEntity campaign = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        campaign.setEndsAt(LocalDateTime.now().minusDays(1));
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.getActiveDiscountContext(DiscountType.BLACK_FRIDAY))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("has expired");
    }

    @Test
    void startDateInPastAndEndDateInFuture_isActive() {
        final DiscountCampaignEntity campaign = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        campaign.setStartsAt(LocalDateTime.now().minusDays(1));
        campaign.setEndsAt(LocalDateTime.now().plusDays(1));
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY))
                .thenReturn(Optional.of(campaign));

        assertThat(service.getActiveDiscountContext(DiscountType.BLACK_FRIDAY))
                .isNotNull();
    }

    @Test
    void nullStartAndEndDate_isAlwaysActive() {
        when(discountCampaignRepository.findByDiscountType(DiscountType.NO_DISCOUNT))
                .thenReturn(Optional.of(DiscountCampaignEntity.builder()
                        .discountType(DiscountType.NO_DISCOUNT)
                        .calculationType(DiscountCalculationType.NONE)
                        .enabled(true)
                        .build()));

        assertThat(service.getActiveDiscountContext(DiscountType.NO_DISCOUNT).calculationType())
                .isEqualTo(DiscountCalculationType.NONE);
    }

    // ----- getAllActiveCampaigns (public/customer surface) -----

    @Test
    void getAllActiveCampaigns_returnsOnlyEnabledAndInWindow() {
        final DiscountCampaignEntity active = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        final DiscountCampaignEntity windowedOk = enabledPercentageCampaign(DiscountType.WINTER_SALES);
        windowedOk.setStartsAt(LocalDateTime.now().minusDays(1));
        windowedOk.setEndsAt(LocalDateTime.now().plusDays(1));
        // Repository.findByEnabledTrue already filters out enabled=false; we still test the
        // window filter which lives in service code.
        final DiscountCampaignEntity notStartedYet = enabledPercentageCampaign(DiscountType.SPRING_SALES);
        notStartedYet.setStartsAt(LocalDateTime.now().plusDays(2));
        final DiscountCampaignEntity expired = enabledPercentageCampaign(DiscountType.BUY_ONE_GET_ONE_FREE);
        expired.setEndsAt(LocalDateTime.now().minusDays(2));

        when(discountCampaignRepository.findByEnabledTrue())
                .thenReturn(List.of(active, windowedOk, notStartedYet, expired));

        final List<DiscountContext> result = service.getAllActiveCampaigns();

        assertThat(result).extracting(DiscountContext::discountType)
                .containsExactlyInAnyOrder(DiscountType.BLACK_FRIDAY, DiscountType.WINTER_SALES);
    }

    @Test
    void getAllActiveCampaigns_emptyRepo_returnsEmptyList() {
        when(discountCampaignRepository.findByEnabledTrue()).thenReturn(List.of());

        assertThat(service.getAllActiveCampaigns()).isEmpty();
    }

    @Test
    void getAllActiveCampaigns_mapsAllRelevantFields() {
        final DiscountCampaignEntity campaign = enabledPercentageCampaign(DiscountType.BLACK_FRIDAY);
        campaign.setMinOrderAmount(new BigDecimal("50.00"));
        campaign.setMaxDiscountAmount(new BigDecimal("200.00"));
        campaign.setFixedAmount(null);
        when(discountCampaignRepository.findByEnabledTrue()).thenReturn(List.of(campaign));

        final DiscountContext ctx = service.getAllActiveCampaigns().get(0);

        assertThat(ctx.discountType()).isEqualTo(DiscountType.BLACK_FRIDAY);
        assertThat(ctx.calculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(ctx.percentage()).isEqualByComparingTo("20");
        assertThat(ctx.minOrderAmount()).isEqualByComparingTo("50.00");
        assertThat(ctx.maxDiscountAmount()).isEqualByComparingTo("200.00");
    }
}
