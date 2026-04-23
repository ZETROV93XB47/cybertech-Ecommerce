package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BankCardMapper}. Verifies the existing null-guard on {@code userEntity}
 * still works (the only mapper in the wave that already had one in pre-F2 baseline).
 */
class BankCardMapperTest {

    private final BankCardMapper mapper = Mappers.getMapper(BankCardMapper.class);

    @Nested
    @DisplayName("mapFromCreationRequestToEntity(BankCardCreationRequestDto)")
    class FromCreationRequest {

        @Test
        void shouldMapAllScalarFieldsAndIgnoreUserEntity() {
            BankCardCreationRequestDto dto = UserDtoFixtures.aValidBankCardCreationRequestBuilder()
                    .cardHolderName("John Doe")
                    .cardNumber("4242424242424242")
                    .expiryDate("12/2099")
                    .cardType(BankCardType.MASTERCARD)
                    .userUuid(UUID.randomUUID())
                    .build();

            BankCardEntity entity = mapper.mapFromCreationRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getCardHolderName()).isEqualTo("John Doe");
            assertThat(entity.getCardNumber()).isEqualTo("4242424242424242");
            assertThat(entity.getExpiryDate()).isEqualTo("12/2099");
            assertThat(entity.getCardType()).isEqualTo(BankCardType.MASTERCARD);
            // userEntity is explicitly @Mapping(target = "userEntity", ignore = true).
            assertThat(entity.getUserEntity()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCreationRequestToEntity((BankCardCreationRequestDto) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<BankCardEntity> entities = mapper.mapFromCreationRequestToEntity(
                    List.of(UserDtoFixtures.aValidBankCardCreationRequest()));

            assertThat(entities).hasSize(1);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromCreationRequestToEntity((Collection<BankCardCreationRequestDto>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromUpdateRequestToEntity(BankCardUpdateRequestDto)")
    class FromUpdateRequest {

        @Test
        void shouldMapAllFieldsIncludingUuid() {
            UUID cardUuid = UUID.randomUUID();
            BankCardUpdateRequestDto dto = new BankCardUpdateRequestDto(
                    cardUuid, "Holder", "5555555555554444", "01/2099", BankCardType.VISA);

            BankCardEntity entity = mapper.mapFromUpdateRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getUuid()).isEqualTo(cardUuid);
            assertThat(entity.getCardHolderName()).isEqualTo("Holder");
            assertThat(entity.getCardNumber()).isEqualTo("5555555555554444");
            assertThat(entity.getExpiryDate()).isEqualTo("01/2099");
            assertThat(entity.getCardType()).isEqualTo(BankCardType.VISA);
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromUpdateRequestToEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("updateEntityFromDto(BankCardUpdateRequestDto, BankCardEntity)")
    class UpdateInPlace {

        @Test
        void shouldOverwriteScalarFieldsButPreserveUserAndUuid() {
            UserEntity originalUser = UserEntityBuilder.aValidUser();
            UUID originalUuid = UUID.randomUUID();
            BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(originalUuid)
                    .userEntity(originalUser)
                    .build();
            BankCardUpdateRequestDto dto = new BankCardUpdateRequestDto(
                    UUID.randomUUID(),
                    "New holder",
                    "4111111111111111",
                    "06/2099",
                    BankCardType.AMERICAN_EXPRESS);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getUuid()).isEqualTo(originalUuid);
            assertThat(entity.getUserEntity()).isSameAs(originalUser);
            assertThat(entity.getCardHolderName()).isEqualTo("New holder");
            assertThat(entity.getCardNumber()).isEqualTo("4111111111111111");
            assertThat(entity.getExpiryDate()).isEqualTo("06/2099");
            assertThat(entity.getCardType()).isEqualTo(BankCardType.AMERICAN_EXPRESS);
        }

        @Test
        void shouldNoOpWhenDtoIsNull() {
            BankCardEntity entity = BankCardEntityBuilder.aValidBankCard();
            String holderBefore = entity.getCardHolderName();

            mapper.updateEntityFromDto(null, entity);

            assertThat(entity.getCardHolderName()).isEqualTo(holderBefore);
        }
    }

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
                    .cardNumber("4242424242424242")
                    .expiryDate("12/2099")
                    .cardType(BankCardType.VISA)
                    .build();

            BankCardResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(entity.getUuid());
            assertThat(dto.getCardHolderName()).isEqualTo("Holder");
            assertThat(dto.getCardNumber()).isEqualTo("4242424242424242");
            assertThat(dto.getExpiryDate()).isEqualTo("12/2099");
            assertThat(dto.getCardType()).isEqualTo(BankCardType.VISA);
            assertThat(dto.getUserUuid()).isEqualTo(userUuid);
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
            assertThat(mapper.mapFromEntityToResponseDto((BankCardEntity) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<BankCardResponseDto> dtos = mapper.mapFromEntityToResponseDto(
                    List.of(BankCardEntityBuilder.aValidBankCard(), BankCardEntityBuilder.aValidBankCard()));

            assertThat(dtos).hasSize(2);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromEntityToResponseDto((Collection<BankCardEntity>) null)).isNull();
        }
    }
}
