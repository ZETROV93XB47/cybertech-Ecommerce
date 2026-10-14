package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BankCardMapper}: masked response only (entities are built from Stripe's
 * PaymentMethod by the service, never mapped from a request DTO).
 */
class BankCardMapperTest {

    private final BankCardMapper mapper = Mappers.getMapper(BankCardMapper.class);

    @Nested
    @DisplayName("mapFromEntityToResponseDto(BankCardEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithUserUuid() {
            UUID userUuid = UUID.randomUUID();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(userUuid).build();
            BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder()
                    .userEntity(user)
                    .cardHolderName("Holder")
                    .expiryDate("12/2099")
                    .cardType(BankCardType.VISA)
                    .build();

            BankCardResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(entity.getUuid());
            assertThat(dto.getCardHolderName()).isEqualTo("Holder");
            //assertThat(dto.getCardNumber()).isEqualTo("4242424242424242");
            assertThat(dto.getExpiryDate()).isEqualTo("12/2099");
            assertThat(dto.getCardType()).isEqualTo(BankCardType.VISA);
            assertThat(dto.getUserUuid()).isEqualTo(userUuid);
        }

        @Test
        void shouldExposeOnlyTheMaskedLastFourDigitsNeverThePaymentMethod() {
            BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder()
                    .lastFourDigits("0341")
                    .stripePaymentMethodId("pm_secret_ref")
                    .build();

            BankCardResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getMaskedNumber()).isEqualTo("**** **** **** 0341");
            assertThat(dto.toString()).doesNotContain("pm_secret_ref");
        }

        @Test
        void shouldReturnNullUserUuidWhenUserEntityIsNull() {
            BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder()
                    .userEntity(null)
                    .build();

            BankCardResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getUserUuid()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto(null)).isNull();
        }
    }
}
