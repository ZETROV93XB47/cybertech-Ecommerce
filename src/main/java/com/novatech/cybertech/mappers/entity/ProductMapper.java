package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper extends BaseMapper<ProductEntity, ProductCreateRequestDto, ProductUpdateRequestDto, ProductResponseDto> {
    @Override
    @Mapping(target = "reservedStock", expression = "java(0)")
    ProductEntity mapFromCreationRequestToEntity(ProductCreateRequestDto productCreateRequestDto);
}
