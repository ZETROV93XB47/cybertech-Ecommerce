package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.entities.ReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReviewMapper extends BaseMapper<ReviewEntity, ReviewCreateRequestDto, ReviewUpdateRequestDto, ReviewResponseDto> {

    @Override
    @Mapping(source = "userEntity.uuid", target = "userUuid")
    @Mapping(source = "productEntity.uuid", target = "productUuid")
    @Mapping(source = "productEntity.name", target = "productName")
    ReviewResponseDto mapFromEntityToResponseDto(ReviewEntity e);

}
