package com.novatech.cybertech.mappers.document;


import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserEventMapper {

    @Mapping(target = "id", ignore = true)                // MongoDB générera l'ID
    @Mapping(target = "timestamp", ignore = true)
    UserEvent toDocument(UserEventDto dto);
}