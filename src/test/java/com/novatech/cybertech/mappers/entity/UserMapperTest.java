package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserMapper}.
 *
 * <p>Verifies BUG-019 fix (null {@code dto.address} no longer wipes {@code entity.address}).
 */
class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Nested
    @DisplayName("mapFromCreationRequestToEntity(UserCreateRequestDto)")
    class FromCreationRequest {

        @Test
        void shouldMapAllScalarFieldsAndExplodedAddress() {
            UserCreateRequestDto dto = UserDtoFixtures.aValidCreateRequest();

            UserEntity entity = mapper.mapFromCreationRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getEmail()).isEqualTo(dto.getEmail());
            assertThat(entity.getFirstName()).isEqualTo(dto.getFirstName());
            assertThat(entity.getLastName()).isEqualTo(dto.getLastName());
            assertThat(entity.getSex()).isEqualTo(dto.getSex());
            assertThat(entity.getPhoneNumber()).isEqualTo(dto.getPhoneNumber());
            assertThat(entity.getFavoriteCommunicationChanel())
                    .isEqualTo(dto.getFavoriteCommunicationChanel());
            assertThat(entity.getBirthDate()).isEqualTo(dto.getBirthDate());
            assertThat(entity.getAddress()).isNotNull();
            assertThat(entity.getAddress().getStreet()).isEqualTo(dto.getStreet());
            assertThat(entity.getAddress().getCity()).isEqualTo(dto.getCity());
            assertThat(entity.getAddress().getZipCode()).isEqualTo(dto.getZipCode());
            assertThat(entity.getAddress().getCountry()).isEqualTo(dto.getCountry());
        }

        @Test
        void shouldDropPasswordKeycloakRoleAndIsActive_documentingTechDebt() {
            // password / bankCardCreationRequestDto / keycloakId / role / isActive are not wired.
            UserEntity entity = mapper.mapFromCreationRequestToEntity(UserDtoFixtures.aValidCreateRequest());

            assertThat(entity.getKeycloakId()).isNull();
            assertThat(entity.getRole()).isNull();
            assertThat(entity.getIsActive()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCreationRequestToEntity((UserCreateRequestDto) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<UserEntity> entities = mapper.mapFromCreationRequestToEntity(
                    List.of(UserDtoFixtures.aValidCreateRequest()));
            assertThat(entities).hasSize(1);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromCreationRequestToEntity((Collection<UserCreateRequestDto>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromUpdateRequestToEntity(UserUpdateRequestDto)")
    class FromUpdateRequest {

        @Test
        void shouldMapMutableFields() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();

            UserEntity entity = mapper.mapFromUpdateRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getUuid()).isEqualTo(dto.getUuid());
            assertThat(entity.getEmail()).isEqualTo(dto.getEmail());
            assertThat(entity.getFirstName()).isEqualTo(dto.getFirstName());
            assertThat(entity.getLastName()).isEqualTo(dto.getLastName());
            // mapStringToAddress places the dto's address string in the street field.
            assertThat(entity.getAddress()).isNotNull();
            assertThat(entity.getAddress().getStreet()).isEqualTo(dto.getAddress());
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromUpdateRequestToEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("updateEntityFromDto(UserUpdateRequestDto, UserEntity)")
    class UpdateInPlace {

        @Test
        void shouldOverwriteScalarFields() {
            UserEntity entity = UserEntityBuilder.aValidUser();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setFirstName("NewFirst");
            dto.setLastName("NewLast");
            dto.setEmail("new@example.com");
            dto.setSex(Sex.M);
            LocalDateTime newDob = LocalDateTime.now().minusYears(20);
            dto.setBirthDate(newDob);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getFirstName()).isEqualTo("NewFirst");
            assertThat(entity.getLastName()).isEqualTo("NewLast");
            assertThat(entity.getEmail()).isEqualTo("new@example.com");
            assertThat(entity.getSex()).isEqualTo(Sex.M);
            assertThat(entity.getBirthDate()).isEqualTo(newDob);
        }

        @Test
        void shouldNotEraseAddressWhenDtoAddressNull() {
            // BUG-019 verification (F2 wave): the address ternary expression preserves
            // entity.address when dto.address is null.
            Address original = Address.builder()
                    .street("Original")
                    .city("Original City")
                    .zipCode("00001")
                    .country("FR")
                    .build();
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().address(original).build();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setAddress(null);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getAddress()).isSameAs(original);
        }

        @Test
        void shouldReplaceAddressWhenDtoAddressProvided() {
            UserEntity entity = UserEntityBuilder.aValidUser();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setAddress("99 rue Z");

            mapper.updateEntityFromDto(dto, entity);

            // mapStringToAddress puts the string into Street with placeholders for the rest.
            assertThat(entity.getAddress().getStreet()).isEqualTo("99 rue Z");
            assertThat(entity.getAddress().getCity()).isEqualTo("Unknown City");
            assertThat(entity.getAddress().getZipCode()).isEqualTo("00000");
            assertThat(entity.getAddress().getCountry()).isEqualTo("Unknown Country");
        }

        @Test
        void shouldNoOpWhenDtoIsNull() {
            UserEntity entity = UserEntityBuilder.aValidUser();
            String emailBefore = entity.getEmail();

            mapper.updateEntityFromDto(null, entity);

            assertThat(entity.getEmail()).isEqualTo(emailBefore);
        }
    }

    @Nested
    @DisplayName("mapFromEntityToResponseDto(UserEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithUsernameAndAddressString() {
            UUID uuid = UUID.randomUUID();
            UserEntity entity = UserEntityBuilder.aValidUserBuilder()
                    .uuid(uuid)
                    .firstName("Jane")
                    .lastName("Doe")
                    .build();

            UserResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(uuid);
            assertThat(dto.getEmail()).isEqualTo(entity.getEmail());
            assertThat(dto.getFirstName()).isEqualTo("Jane");
            assertThat(dto.getLastName()).isEqualTo("Doe");
            assertThat(dto.getUsername()).isEqualTo("jane.doe");
            assertThat(dto.getAddress()).isEqualTo("1 rue de Test, 75001 Paris, FR");
            assertThat(dto.getBirthDate()).isNotNull();
            assertThat(dto.getRole()).isEqualTo(entity.getRole());
            assertThat(dto.getKeycloakId()).isEqualTo(entity.getKeycloakId());
        }

        @Test
        void shouldHandleNullAddress() {
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().address(null).build();

            UserResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getAddress()).isNull();
        }

        @Test
        void shouldHandleNullBirthDate() {
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().birthDate(null).build();

            UserResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getBirthDate()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto((UserEntity) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<UserResponseDto> dtos = mapper.mapFromEntityToResponseDto(
                    List.of(UserEntityBuilder.aValidUser(), UserEntityBuilder.aValidUser()));

            assertThat(dtos).hasSize(2);
        }
    }

    @Nested
    @DisplayName("default helpers — mapStringToAddress / mapAddressToString / usernameMapper")
    class Helpers {

        @Test
        void mapStringToAddress_shouldReturnNullForNull() {
            assertThat(mapper.mapStringToAddress(null)).isNull();
        }

        @Test
        void mapStringToAddress_shouldUsePlaceholdersForOtherFields() {
            Address addr = mapper.mapStringToAddress("Some street");
            assertThat(addr.getStreet()).isEqualTo("Some street");
            assertThat(addr.getCity()).isEqualTo("Unknown City");
            assertThat(addr.getZipCode()).isEqualTo("00000");
            assertThat(addr.getCountry()).isEqualTo("Unknown Country");
        }

        @Test
        void mapAddressToString_shouldReturnNullForNull() {
            assertThat(mapper.mapAddressToString(null)).isNull();
        }

        @Test
        void mapAddressToString_shouldFormatFields() {
            Address addr = Address.builder()
                    .street("12 rue X").city("Paris").zipCode("75002").country("FR")
                    .build();
            assertThat(mapper.mapAddressToString(addr)).isEqualTo("12 rue X, 75002 Paris, FR");
        }

        @Test
        void usernameMapper_shouldReturnNullWhenAnyPartIsNull() {
            assertThat(mapper.usernameMapper(null, "Doe")).isNull();
            assertThat(mapper.usernameMapper("Jane", null)).isNull();
        }

        @Test
        void usernameMapper_shouldLowercaseTrimAndJoinWithDot() {
            assertThat(mapper.usernameMapper("  Jane  ", "DOE")).isEqualTo("jane.doe");
        }
    }
}
