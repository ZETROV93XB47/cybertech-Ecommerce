package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for {@link BankCardEntity}.
 *
 * <p>BUG-036 (PCI-DSS) rationale: the response DTO builds {@code maskedNumber} from
 * {@code lastFourDigits} so the plaintext PAN never leaks out of the server — even if a row
 * was written before the fix, only the last four digits surface via the masked field. We
 * also explicitly ignore {@code encryptedNumber} when producing the response so the base64
 * ciphertext cannot accidentally leak either.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BankCardMapper extends BaseMapper<BankCardEntity, BankCardCreationRequestDto, BankCardUpdateRequestDto, BankCardResponseDto> {

    @Override
    @Mapping(target = "userUuid", expression = "java(entity.getUserEntity() != null ? entity.getUserEntity().getUuid() : null)")
    @Mapping(target = "maskedNumber", expression = "java(entity.getLastFourDigits() != null ? \"**** **** **** \" + entity.getLastFourDigits() : null)")
    BankCardResponseDto mapFromEntityToResponseDto(BankCardEntity entity);

    @Override
    @Mapping(target = "userEntity", ignore = true) // Géré manuellement dans le service
    @Mapping(target = "encryptedNumber", ignore = true) // BUG-036: set by the service via CardEncryptionService
    @Mapping(target = "lastFourDigits", ignore = true) // BUG-036: computed by the service, never from the raw DTO
    @Mapping(target = "isDefault", ignore = true) // BUG-038: never user-settable on creation
    BankCardEntity mapFromCreationRequestToEntity(BankCardCreationRequestDto dto);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "userEntity", ignore = true)
    @Mapping(target = "encryptedNumber", ignore = true)
    @Mapping(target = "lastFourDigits", ignore = true)
    @Mapping(target = "isDefault", ignore = true)
    void updateEntityFromDto(BankCardUpdateRequestDto dto, @MappingTarget BankCardEntity entity);
}
