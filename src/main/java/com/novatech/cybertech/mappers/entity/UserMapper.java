package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<UserEntity, UserCreateRequestDto, UserUpdateRequestDto, UserResponseDto> {
    // Il me faut l'uuid pour pouvoir retrouver le User, pas pour le maj
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(UserUpdateRequestDto dto, @MappingTarget UserEntity entity);
}
