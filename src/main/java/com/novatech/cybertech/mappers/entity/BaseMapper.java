package com.novatech.cybertech.mappers.entity;

import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Collection;

/*
ENTITY, CREATE, UPDATE, RESPONSE
 */

public interface BaseMapper<E, C, U, R> {

    R mapFromEntityToResponseDto(E e);

    Collection<R> mapFromEntityToResponseDto(Collection<E> entities);

    E mapFromCreationRequestToEntity(C r);

    Collection<E> mapFromCreationRequestToEntity(Collection<C> rs);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    E mapFromUpdateRequestToEntity(U u);
}



//ENTITY mapFromCreationRequestDtoToEntity(CREATE dto);
//Collection<ENTITY> mapFromCreationRequestDtoToEntity(Collection<CREATE> dtos);
