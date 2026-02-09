package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BankCardMapper extends BaseMapper<BankCardEntity, BankCardCreationRequestDto, BankCardUpdateRequestDto, BankCardResponseDto> {

    @Override
    @Mapping(target = "userUuid", expression = "java(entity.getUserEntity() != null ? entity.getUserEntity().getUuid() : null)")
    BankCardResponseDto mapFromEntityToResponseDto(BankCardEntity entity);

    @Override
    @Mapping(target = "userEntity", ignore = true) // Géré manuellement dans le service
    BankCardEntity mapFromCreationRequestToEntity(BankCardCreationRequestDto dto);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "userEntity", ignore = true)
    void updateEntityFromDto(BankCardUpdateRequestDto dto, @MappingTarget BankCardEntity entity);
}