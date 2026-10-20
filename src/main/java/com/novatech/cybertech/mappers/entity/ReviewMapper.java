package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.MyReviewDto;
import com.novatech.cybertech.dto.response.review.PublicReviewDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReviewMapper extends BaseMapper<ReviewEntity, ReviewCreateRequestDto, ReviewUpdateRequestDto, ReviewResponseDto> {

    @Override
    @Mapping(source = "userEntity.uuid", target = "userUuid")
    @Mapping(source = "productEntity.uuid", target = "productUuid")
    @Mapping(source = "productEntity.name", target = "productName")
    ReviewResponseDto mapFromEntityToResponseDto(ReviewEntity e);

    /** Public view of a review: the author's first name only (blank as null), the posting day only. */
    @Mapping(source = "userEntity", target = "authorFirstName", qualifiedByName = "firstNameOnly")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "dayOnly")
    PublicReviewDto mapFromEntityToPublicReviewDto(ReviewEntity e);

    @Mapping(source = "productEntity.uuid", target = "productUuid")
    @Mapping(source = "productEntity.name", target = "productName")
    MyReviewDto mapFromEntityToMyReviewDto(ReviewEntity e);

    @Named("firstNameOnly")
    default String firstNameOnly(final UserEntity user) {
        return user != null && StringUtils.hasText(user.getFirstName()) ? user.getFirstName().strip() : null;
    }

    @Named("dayOnly")
    default LocalDate dayOnly(final LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

}
