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
 * <p>Verifies the fix for null {@code dto.address} no longer wiping {@code entity.address}.
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
            assertThat(entity.getAddress()).isNotNull();
            assertThat(entity.getAddress().getStreet()).isEqualTo(dto.getStreet());
            assertThat(entity.getAddress().getCity()).isEqualTo(dto.getCity());
            assertThat(entity.getAddress().getZipCode()).isEqualTo(dto.getZipCode());
            assertThat(entity.getAddress().getCountry()).isEqualTo(dto.getCountry());
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
        void shouldNotEraseEmailSexOrBirthDateWhenDtoFieldsNull() {
            // Bug fix: a partial update (e.g. updateMe(), which never sets email/sex/birthDate on
            // the adapted DTO) must NOT wipe these NOT-NULL columns to null — null on the DTO means
            // "not modified", not "clear the field".
            UserEntity entity = UserEntityBuilder.aValidUserBuilder()
                    .email("original@example.com")
                    .sex(Sex.F)
                    .birthDate(LocalDateTime.now().minusYears(25))
                    .build();
            String originalEmail = entity.getEmail();
            Sex originalSex = entity.getSex();
            LocalDateTime originalBirthDate = entity.getBirthDate();

            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setEmail(null);
            dto.setSex(null);
            dto.setBirthDate(null);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getEmail()).isEqualTo(originalEmail);
            assertThat(entity.getSex()).isEqualTo(originalSex);
            assertThat(entity.getBirthDate()).isEqualTo(originalBirthDate);
            // Non-null fields on the DTO must still overwrite as before.
            assertThat(entity.getFirstName()).isEqualTo(dto.getFirstName());
            assertThat(entity.getLastName()).isEqualTo(dto.getLastName());
        }

        @Test
        void shouldNotEraseAddressWhenNoAddressFieldProvided() {
            // A partial update where the caller sends none of street/city/zipCode/country
            // must leave the existing address entirely untouched.
            Address original = Address.builder()
                    .street("Original")
                    .city("Original City")
                    .zipCode("00001")
                    .country("FR")
                    .build();
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().address(original).build();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setStreet(null);
            dto.setCity(null);
            dto.setZipCode(null);
            dto.setCountry(null);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getAddress().getStreet()).isEqualTo("Original");
            assertThat(entity.getAddress().getCity()).isEqualTo("Original City");
            assertThat(entity.getAddress().getZipCode()).isEqualTo("00001");
            assertThat(entity.getAddress().getCountry()).isEqualTo("FR");
        }

        @Test
        void shouldPatchOnlyTheProvidedAddressFieldsPreservingTheRest() {
            // Bug fix: patching just the street must NOT wipe city/zipCode/country with
            // placeholder values — only the fields actually sent are changed.
            Address original = Address.builder()
                    .street("Original")
                    .city("Original City")
                    .zipCode("00001")
                    .country("FR")
                    .build();
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().address(original).build();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setStreet("99 rue Z");
            dto.setCity(null);
            dto.setZipCode(null);
            dto.setCountry(null);

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getAddress().getStreet()).isEqualTo("99 rue Z");
            assertThat(entity.getAddress().getCity()).isEqualTo("Original City");
            assertThat(entity.getAddress().getZipCode()).isEqualTo("00001");
            assertThat(entity.getAddress().getCountry()).isEqualTo("FR");
        }

        @Test
        void shouldReplaceAllAddressFieldsWhenAllProvided() {
            UserEntity entity = UserEntityBuilder.aValidUser();
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setStreet("99 rue Z");
            dto.setCity("Lyon");
            dto.setZipCode("69000");
            dto.setCountry("FR");

            mapper.updateEntityFromDto(dto, entity);

            assertThat(entity.getAddress().getStreet()).isEqualTo("99 rue Z");
            assertThat(entity.getAddress().getCity()).isEqualTo("Lyon");
            assertThat(entity.getAddress().getZipCode()).isEqualTo("69000");
            assertThat(entity.getAddress().getCountry()).isEqualTo("FR");
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
    @DisplayName("default helpers — mapAddressToString / usernameMapper")
    class Helpers {

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
