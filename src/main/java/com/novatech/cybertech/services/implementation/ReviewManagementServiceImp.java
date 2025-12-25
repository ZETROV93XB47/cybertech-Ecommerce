package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import com.novatech.cybertech.exceptions.ReviewNotFoundException;
import com.novatech.cybertech.mappers.entity.ReviewMapper;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.services.core.ModerationService;
import com.novatech.cybertech.services.core.ReviewManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import static com.novatech.cybertech.utils.DataGenerator.generateReviewEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewManagementServiceImp implements ReviewManagementService {

    private static final float HATEFUL_COMMENT_SCORE_THRESHOLD = 0.7f;

    private final ReviewMapper reviewMapper;
    private final ReviewRepository reviewRepository;
    private final ModerationService moderationService;

    @Override
    @Transactional(readOnly = true)
    public Collection<ReviewResponseDto> getAll() {
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponseDto getByUUID(UUID uuid) {
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.findByUuid(uuid).orElseThrow(() -> new ReviewNotFoundException("No review with the UUID : " + uuid + " found")));
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<ReviewResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public ReviewResponseDto create(ReviewCreateRequestDto reviewCreateRequestDto) {
        ReviewEntity reviewEntity = reviewMapper.mapFromCreationRequestToEntity(reviewCreateRequestDto);

        ModerationResponseDto moderationResponseDto = moderationService.checkIfIsHateful(reviewEntity.getComment());

        if (moderationResponseDto.getScore() > HATEFUL_COMMENT_SCORE_THRESHOLD)
            throw new CommentPostNotAllowedException("Your comment seems similar to other hateful comments detected on our website, our moderation team will review it and decide to post it or not.");

        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(reviewEntity));
    }

    @Transactional
    public Collection<ReviewResponseDto> createAutomatically(Collection<ReviewEntity> reviews) {
        return new ArrayList<>(reviewMapper.mapFromEntityToResponseDto(reviews.stream().map(reviewRepository::save).toList()));
    }

    @Override
    @Transactional
    public ReviewResponseDto update(final ReviewUpdateRequestDto reviewCreateRequestDto) {
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(reviewMapper.mapFromUpdateRequestToEntity(reviewCreateRequestDto)));
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        reviewRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        reviewRepository.deleteAllByUuidIn(uuids);
    }

    @Transactional
    public ReviewResponseDto saveReview() {
        ReviewEntity reviewEntity = generateReviewEntity();
        log.info(reviewEntity.toString());

        ModerationResponseDto moderationResponseDto = moderationService.checkIfIsHateful(reviewEntity.getComment());

        log.info("moderation service response : {}", moderationResponseDto.toString());

        if (moderationResponseDto.getScore() > HATEFUL_COMMENT_SCORE_THRESHOLD)
            throw new CommentPostNotAllowedException("Your comment seems similar to other hateful comments detected on our website, our moderation team will review it and decide to post it or not.");

        log.info("review saved");
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(reviewEntity));

    }
}
