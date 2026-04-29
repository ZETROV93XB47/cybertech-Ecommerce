package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ReviewCrudControllerApiSpec;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;
import com.novatech.cybertech.services.core.ReviewManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.REVIEW_CRUD_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = REVIEW_CRUD_CONTROLLER_BASE_PATH)
@Tag(name = "ReviewController", description = "API for Review management")
public class ReviewCrudController implements ReviewCrudControllerApiSpec {

    private final ReviewManagementService reviewService;


    @Override
    @PreAuthorize("hasRole('USER')")
    // FIX(SEC-INCONSISTENCY): explicit auth check matching the rest of the controller; previously relied on default authenticated() but was inconsistent with siblings using hasRole
    @GetMapping(value = "/get/{reviewUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> getReviewByUuid(final @PathVariable("reviewUuid") UUID reviewUuid) {
        return ResponseEntity.status(HttpStatus.OK).body(reviewService.getByUUID(reviewUuid));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> createReview(@Valid @RequestBody final ReviewCreateRequestDto reviewCreateRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(reviewCreateRequestDto, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    @PatchMapping(value = "/update/{reviewUuid}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewResponseDto> updateReview(@Valid @RequestBody final ReviewUpdateRequestDto reviewUpdateRequestDto, final @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(reviewService.update(reviewUpdateRequestDto, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasRole('USER')")
    @DeleteMapping(value = "/delete/{reviewUuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteReviewByUuid(final @PathVariable("reviewUuid") UUID reviewUuid, @AuthenticationPrincipal final Jwt jwt) {
        reviewService.deleteByUUID(reviewUuid, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists products the authenticated user has bought (orders in PAID / SHIPPED / DELIVERED)
     * but has not yet reviewed. Each entry carries the {@code orderUuid} the frontend needs
     * to forward in {@code POST /create}.
     */
    @PreAuthorize("hasRole('USER')")
    @GetMapping(value = "/reviewable", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReviewableProductDto>> getReviewableProducts(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(reviewService.getReviewableProducts(jwt.getSubject()));
    }

}
