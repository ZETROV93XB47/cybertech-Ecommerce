package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for {@link BankCardEntity}.
 *
 * <p>The response DTO builds {@code maskedNumber} from {@code lastFourDigits} (reported by
 * Stripe): the backend never sees more of the card number than that. Entities are built and
 * updated by {@code BankCardManagementServiceImp} from Stripe's PaymentMethod, never from a
 * request DTO.</p>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BankCardMapper {

    @Mapping(target = "userUuid", expression = "java(entity.getUserEntity() != null ? entity.getUserEntity().getUuid() : null)")
    @Mapping(target = "maskedNumber", expression = "java(entity.getLastFourDigits() != null ? \"**** **** **** \" + entity.getLastFourDigits() : null)")
    BankCardResponseDto mapFromEntityToResponseDto(BankCardEntity entity);
}
