package com.novatech.cybertech.mappers.gorse;

import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GorseMapper {

    @Mapping(target = "itemId", source = "uuid")
    @Mapping(target = "isHidden", expression = "java(false)")
    @Mapping(target = "labels", expression = "java(java.util.List.of(product.getCategory(), product.getBrand().name()))")
    @Mapping(target = "timestamp", expression = "java(product.getCreatedAt().toInstant(java.time.ZoneOffset.UTC))")
    GorseItemDto toItemDto(ProductEntity product);

    @Mapping(target = "userId", source = "keycloakId")
    GorseUserDto toUserDto(UserEntity user);

    /**
     * {@code feedbackType} carries the {@link com.novatech.cybertech.entities.enums.UserEventType}
     * name verbatim — see {@link GorseFeedbackDto} javadoc for why no separate taxonomy is used.
     */
    @Mapping(target = "feedbackType", expression = "java(event.getEventType().name())")
    @Mapping(target = "itemId", source = "productId")
    GorseFeedbackDto toFeedbackDto(UserEvent event);
}
