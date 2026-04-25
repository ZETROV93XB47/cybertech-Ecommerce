package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.DiscountCampaignEntity;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.exceptions.DiscountTypeNotActiveException;
import com.novatech.cybertech.repositories.DiscountCampaignRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountCampaignAdminServiceImpTest {

    @Mock private DiscountCampaignRepository discountCampaignRepository;
    @Mock private DiscountCampaignServiceImp discountCampaignService;

    @InjectMocks
    private DiscountCampaignAdminServiceImp service;

    private DiscountCampaignEntity entity(final DiscountType type, final DiscountCalculationType calc, final boolean enabled) {
        return DiscountCampaignEntity.builder()
                .uuid(UUID.randomUUID())
                .discountType(type)
                .calculationType(calc)
                .enabled(enabled)
                .percentage(new BigDecimal("20.00"))
                .priority(50)
                .build();
    }

    @Test
    @DisplayName("getAll returns every campaign sorted by DiscountType")
    void getAllSorted() {
        when(discountCampaignRepository.findAll()).thenReturn(List.of(
                entity(DiscountType.WINTER_SALES, DiscountCalculationType.PERCENTAGE, true),
                entity(DiscountType.BLACK_FRIDAY, DiscountCalculationType.PERCENTAGE, false)));

        final List<DiscountCampaignResponseDto> result = service.getAll();

        assertThat(result).hasSize(2);
        // Enum order: BUY_ONE_GET_ONE_FREE, BLACK_FRIDAY, WINTER_SALES, SPRING_SALES, NO_DISCOUNT
        assertThat(result.get(0).discountType()).isEqualTo(DiscountType.BLACK_FRIDAY);
        assertThat(result.get(1).discountType()).isEqualTo(DiscountType.WINTER_SALES);
    }

    @Test
    @DisplayName("getByDiscountType returns the matching campaign")
    void getByDiscountTypeFound() {
        final DiscountCampaignEntity e = entity(DiscountType.BLACK_FRIDAY, DiscountCalculationType.PERCENTAGE, true);
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY)).thenReturn(Optional.of(e));

        final DiscountCampaignResponseDto result = service.getByDiscountType(DiscountType.BLACK_FRIDAY);

        assertThat(result.discountType()).isEqualTo(DiscountType.BLACK_FRIDAY);
        assertThat(result.calculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);
        assertThat(result.enabled()).isTrue();
    }

    @Test
    @DisplayName("getByDiscountType throws when missing")
    void getByDiscountTypeMissing() {
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByDiscountType(DiscountType.BLACK_FRIDAY))
                .isInstanceOf(DiscountTypeNotActiveException.class)
                .hasMessageContaining("BLACK_FRIDAY");
    }

    @Test
    @DisplayName("update applies non-null fields, persists, and evicts the cache")
    void updateAppliesAndEvicts() {
        final DiscountCampaignEntity existing = entity(DiscountType.BLACK_FRIDAY, DiscountCalculationType.PERCENTAGE, false);
        when(discountCampaignRepository.findByDiscountType(DiscountType.BLACK_FRIDAY)).thenReturn(Optional.of(existing));
        when(discountCampaignRepository.save(any(DiscountCampaignEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        final LocalDateTime starts = LocalDateTime.now();
        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                /* enabled */ true,
                /* calculationType */ null,
                /* percentage */ new BigDecimal("35.00"),
                /* fixedAmount */ null,
                /* minOrderAmount */ new BigDecimal("50.00"),
                /* maxDiscountAmount */ new BigDecimal("100.00"),
                /* startsAt */ starts,
                /* endsAt */ null,
                /* priority */ 75);

        final DiscountCampaignResponseDto result = service.update(DiscountType.BLACK_FRIDAY, patch);

        final ArgumentCaptor<DiscountCampaignEntity> saveCap = ArgumentCaptor.forClass(DiscountCampaignEntity.class);
        verify(discountCampaignRepository).save(saveCap.capture());
        final DiscountCampaignEntity saved = saveCap.getValue();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPercentage()).isEqualByComparingTo("35.00");
        assertThat(saved.getMinOrderAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getMaxDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getStartsAt()).isEqualTo(starts);
        assertThat(saved.getPriority()).isEqualTo(75);
        // calculationType was null in patch — left untouched
        assertThat(saved.getCalculationType()).isEqualTo(DiscountCalculationType.PERCENTAGE);

        verify(discountCampaignService).evictCache(DiscountType.BLACK_FRIDAY);
        assertThat(result.enabled()).isTrue();
        assertThat(result.percentage()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("update with all-null fields leaves the entity unchanged but still evicts the cache")
    void updateWithAllNulls() {
        final DiscountCampaignEntity existing = entity(DiscountType.WINTER_SALES, DiscountCalculationType.PERCENTAGE, true);
        when(discountCampaignRepository.findByDiscountType(DiscountType.WINTER_SALES)).thenReturn(Optional.of(existing));
        when(discountCampaignRepository.save(any(DiscountCampaignEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        final DiscountCampaignUpdateRequestDto patch = new DiscountCampaignUpdateRequestDto(
                null, null, null, null, null, null, null, null, null);

        service.update(DiscountType.WINTER_SALES, patch);

        verify(discountCampaignRepository).save(existing);
        verify(discountCampaignService).evictCache(DiscountType.WINTER_SALES);
    }

    @Test
    @DisplayName("update on missing campaign throws DiscountTypeNotActiveException without saving")
    void updateMissing() {
        when(discountCampaignRepository.findByDiscountType(DiscountType.SPRING_SALES)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(DiscountType.SPRING_SALES,
                new DiscountCampaignUpdateRequestDto(true, null, null, null, null, null, null, null, null)))
                .isInstanceOf(DiscountTypeNotActiveException.class);

        verify(discountCampaignRepository, never()).save(any());
        verify(discountCampaignService, never()).evictCache(any());
    }
}
