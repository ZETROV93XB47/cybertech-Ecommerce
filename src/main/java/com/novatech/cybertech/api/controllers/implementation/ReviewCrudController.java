package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ReviewCrudControllerApiSpec;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.services.implementation.ReviewManagementServiceImp;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.REVIEW_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(REVIEW_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "ReviewController", description = "API for Review management")
public class ReviewCrudController implements ReviewCrudControllerApiSpec {

    private final ReviewManagementServiceImp reviewService;

    @Override
    @GetMapping(value = "/get/{reviewUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> getReviewByUuid(@PathVariable("reviewUuid") UUID reviewUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(reviewService.getByUUID(reviewUuid));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> createReview(@Valid @RequestBody ReviewCreateRequestDto reviewCreateRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(reviewCreateRequestDto));
    }

    @Override
    @PatchMapping(value = "/update/{reviewUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> updateReview(final ReviewUpdateRequestDto reviewUpdateRequestDto) {
        return ResponseEntity.status(HttpStatus.OK).body(reviewService.update(reviewUpdateRequestDto));
    }

    @Override
    @DeleteMapping(value = "/delete/{reviewUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteReviewByUuid(UUID reviewUuid) {
        reviewService.deleteByUUID(reviewUuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/create/auto", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> createReview() {
        log.info("The following class has been called : {}", this.getClass().getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.saveReview());
    }

}
