package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.valueObjects.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<UserEntity, UserCreateRequestDto, UserUpdateRequestDto, UserResponseDto> {
    
    // Il me faut l'uuid pour pouvoir retrouver le User, pas pour le maj
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "address", expression = "java(mapStringToAddress(dto.getAddress()))")
    void updateEntityFromDto(UserUpdateRequestDto dto, @MappingTarget UserEntity entity);

    @Override
    @Mapping(target = "address.street", source = "street")
    @Mapping(target = "address.city", source = "city")
    @Mapping(target = "address.zipCode", source = "zipCode")
    @Mapping(target = "address.country", source = "country")
    UserEntity mapFromCreationRequestToEntity(UserCreateRequestDto dto);

    @Override
    @Mapping(target = "address", expression = "java(mapAddressToString(entity.getAddress()))")
    UserResponseDto mapFromEntityToResponseDto(UserEntity entity);

    default Address mapStringToAddress(String address) {
        if (address == null) return null;
        return new Address(address, "Unknown City", "00000", "Unknown Country");
    }

    default String mapAddressToString(Address address) {
        if (address == null) return null;
        return String.format("%s, %s %s, %s", address.getStreet(), address.getZipCode(), address.getCity(), address.getCountry());
    }
}
