package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import com.novatech.cybertech.entities.StockAlertEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ProductMapper.class})
public interface StockAlertMapper {

    StockAlertResponseDto toResponseDto(StockAlertEntity entity);
}
