package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.valueObjects.Address;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<UserEntity, UserCreateRequestDto, UserUpdateRequestDto, UserResponseDto> {

    // Update = patch partiel : un champ du DTO à null signifie "non modifié", pas "à effacer"
    // (email/sex/birthDate sont NOT NULL en base — un écrasement par null ferait planter le flush).
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // Il me faut l'uuid pour pouvoir retrouver le User, pas pour le maj
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "address", ignore = true)
    void updateEntityFromDto(UserUpdateRequestDto dto, @MappingTarget UserEntity entity);

    /**
     * Applies the (possibly partial) address fields of an update request onto the managed
     * user's immutable {@link Address}. Mirrors {@link OrderMapper#mergeShippingAddressFromUpdate}
     * — only the fields present in the DTO are changed, the rest of the existing address (and
     * any field never sent) is preserved instead of being overwritten with placeholder values.
     */
    @AfterMapping
    default void mergeAddressFromUpdate(final UserUpdateRequestDto dto, @MappingTarget final UserEntity entity) {
        Address current = entity.getAddress();
        if (current == null) {
            entity.setAddress(new Address(dto.getStreet(), dto.getCity(), dto.getZipCode(), dto.getCountry()));
            return;
        }
        if (dto.getStreet() != null) {
            current = current.withStreet(dto.getStreet());
        }
        if (dto.getCity() != null) {
            current = current.withCity(dto.getCity());
        }
        if (dto.getZipCode() != null) {
            current = current.withZipCode(dto.getZipCode());
        }
        if (dto.getCountry() != null) {
            current = current.withCountry(dto.getCountry());
        }
        entity.setAddress(current);
    }

    // Mapping direct des champs éclatés du DTO vers l'objet Address de l'entité
    @Override
    @Mapping(target = "address.street", source = "street")
    @Mapping(target = "address.city", source = "city")
    @Mapping(target = "address.zipCode", source = "zipCode")
    @Mapping(target = "address.country", source = "country")
    UserEntity mapFromCreationRequestToEntity(UserCreateRequestDto dto);

    // Idem pour le DTO d'update (fresh entity, pas de patch partiel ici) : mêmes champs éclatés.
    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "address.street", source = "street")
    @Mapping(target = "address.city", source = "city")
    @Mapping(target = "address.zipCode", source = "zipCode")
    @Mapping(target = "address.country", source = "country")
    UserEntity mapFromUpdateRequestToEntity(UserUpdateRequestDto dto);

    @Override
    @Mapping(target = "username", expression = "java(usernameMapper(entity.getFirstName(), entity.getLastName()))")
    @Mapping(target = "address", expression = "java(mapAddressToString(entity.getAddress()))")
    UserResponseDto mapFromEntityToResponseDto(UserEntity entity);

    default String mapAddressToString(Address address) {
        if (address == null) return null;
        return String.format("%s, %s %s, %s", address.getStreet(), address.getZipCode(), address.getCity(), address.getCountry());
    }

    default String usernameMapper(String firstName, String lastName) {
        if (firstName == null || lastName == null) return null;
        return firstName.trim().toLowerCase() + "." + lastName.trim().toLowerCase();
    }
}
