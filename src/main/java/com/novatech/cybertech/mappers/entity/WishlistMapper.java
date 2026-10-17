package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.response.wishlist.WishlistResponseDto;
import com.novatech.cybertech.entities.WishlistEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
import java.util.List;

@Mapper(componentModel = "spring", uses = {ProductMapper.class})
public interface WishlistMapper {

    @Mapping(target = "product", source = "product")
    WishlistResponseDto toResponseDto(WishlistEntity entity);

    List<WishlistResponseDto> toResponseDtoList(Collection<WishlistEntity> entities);
}