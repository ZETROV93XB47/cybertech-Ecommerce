package com.novatech.cybertech.api.controllers.implementation.review;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ReviewCrudController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import com.novatech.cybertech.exceptions.ReviewNotFoundException;
import com.novatech.cybertech.exceptions.UserNotAuthorOfReviewException;
import com.novatech.cybertech.fixtures.dto.ReviewDtoFixtures;
import com.novatech.cybertech.services.implementation.ReviewManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sibling test class to {@code ReviewCrudControllerTest} — adds branches the canonical reference
 * does NOT cover (rating bounds, GET 404, malformed JSON, anonymous access, admin-vs-other-user
 * delete, CommentPostNotAllowedException). The canonical file is the user's gold-standard style
 * and must NOT be modified — see SA-W2.3 brief.
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ReviewCrudController.class)
class ReviewCrudControllerAdditionalTest {

    private static final String CREATE_REVIEW_ENDPOINT = "/api/v1/services/review/create";
    private static final String UPDATE_REVIEW_ENDPOINT = "/api/v1/services/review/update/{reviewUuid}";
    private static final String GET_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/get/{reviewUuid}";
    private static final String DELETE_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/delete/{reviewUuid}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReviewManagementServiceImp reviewService;

    // ----- GET non-existent --------------------------------------------------------------

    @Test
    void shouldReturn404WhenGettingNonExistentReview() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No review with the UUID : " + reviewUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(reviewService.getByUUID(reviewUuid))
                .thenThrow(new ReviewNotFoundException("No review with the UUID : " + reviewUuid + " found"));

        mockMvc.perform(get(GET_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Rating bounds on CREATE -------------------------------------------------------

    @Test
    void shouldRejectCreateWithRatingZero() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequestBuilder().rating(0).build();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldRejectCreateWithRatingSix() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequestBuilder().rating(6).build();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldAcceptCreateWithRatingOne() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequestBuilder().rating(1).build();
        ReviewResponseDto response = ReviewDtoFixtures.aSampleReviewResponseBuilder().rating(1).build();

        when(reviewService.create(any(ReviewCreateRequestDto.class), anyString())).thenReturn(response);

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldAcceptCreateWithRatingFive() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequestBuilder().rating(5).build();
        ReviewResponseDto response = ReviewDtoFixtures.aSampleReviewResponseBuilder().rating(5).build();

        when(reviewService.create(any(ReviewCreateRequestDto.class), anyString())).thenReturn(response);

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    // ----- Rating bounds on UPDATE -------------------------------------------------------

    @Test
    void shouldRejectUpdateWithRatingOutOfRange() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        ReviewUpdateRequestDto dto = ReviewDtoFixtures.aValidUpdateRequestBuilder()
                .reviewUuid(reviewUuid)
                .rating(7)
                .build();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid Request or Request Poorly Constructed")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUuid)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Malformed JSON body (BUG-2503 — F2 fix verification) -------------------------

    /**
     * F2 added an {@code HttpMessageNotReadableException} handler in {@code ErrorManagementController}
     * (lines 45-49), so a malformed body now resolves to 400 TECHNICAL with message
     * "Malformed JSON request body". Pinning the post-fix behaviour here.
     */
    @Test
    void shouldReturn400ForMalformedJsonBody_BUG_2503_postFix() throws Exception {
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Malformed JSON request body")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Unauthenticated CREATE/UPDATE/DELETE (BUG-2504) ------------------------------

    /**
     * SA2.5 documented BUG-2504: anonymous calls returned 403 instead of 401. SA-W0 wired the
     * production {@code CustomAuthenticationEntryPoint} into {@link TestSecurityConfig} so anonymous
     * requests now resolve to 401 — confirming the fix at the slice-test level.
     */
    @Test
    void shouldReturn401WhenCreatingReviewAnonymously_BUG_2504_postFix() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequest();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenUpdatingReviewAnonymously_BUG_2504_postFix() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        ReviewUpdateRequestDto dto = ReviewDtoFixtures.aValidUpdateRequestBuilder()
                .reviewUuid(reviewUuid)
                .build();

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenDeletingReviewAnonymously_BUG_2504_postFix() throws Exception {
        UUID reviewUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ----- Admin attempting to delete another user's review -----------------------------

    /**
     * The controller has no admin-bypass branch — admins authenticate as ROLE_ADMIN but the service
     * still throws {@link UserNotAuthorOfReviewException} (mapped to 403 FUNCTIONAL) when the JWT
     * subject does not own the review. Pinned via passing test (no @Disabled needed: this is the
     * deliberately-current behaviour and any future "admin can moderate" change should flip the
     * assertion explicitly). Note: ROLE_ADMIN does not satisfy the {@code @PreAuthorize("hasRole('USER')")}
     * either, so the @PreAuthorize layer denies the admin first → 403 ACCESS_DENIED via the
     * F1.1 advice handler (also FUNCTIONAL). Either path resolves to 403 FUNCTIONAL — assert that
     * shape only.
     */
    @Test
    void shouldReturn403WhenAdminDeletesAnotherUsersReview_documentingCurrentBehaviour() throws Exception {
        UUID reviewUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .with(jwtAdmin("admin-keycloak-id"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON));
    }

    // ----- CommentPostNotAllowedException (BUG-004 — F2 fix verification) ---------------

    /**
     * F2 added a dedicated handler at lines 172-176 of {@code ErrorManagementController} mapping
     * {@link CommentPostNotAllowedException} to 403 FORBIDDEN with FUNCTIONAL error type. Confirming
     * the post-fix behaviour here — replaces the SA2.5 disabled "desired behaviour" pin.
     */
    @Test
    void shouldReturn403FunctionalWhenCommentPostNotAllowed_BUG_004_postFix() throws Exception {
        ReviewCreateRequestDto dto = ReviewDtoFixtures.aValidCreateRequest();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("User cannot comment on this product (no completed order)")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(reviewService.create(any(ReviewCreateRequestDto.class), anyString()))
                .thenThrow(new CommentPostNotAllowedException("User cannot comment on this product (no completed order)"));

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Update with subject mismatch — passes through to service ---------------------

    @Test
    void shouldReturn403WhenUpdatePropagatesUserNotAuthorOfReview() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        ReviewUpdateRequestDto dto = ReviewDtoFixtures.aValidUpdateRequestBuilder()
                .reviewUuid(reviewUuid)
                .build();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Current review Doesn't belongs to the connected user")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(reviewService.update(any(ReviewUpdateRequestDto.class), anyString()))
                .thenThrow(new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user"));

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUuid)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Delete happy path with a different keycloakId ---------------------------------

    @Test
    void shouldDeleteReviewSuccessfullyForOwningUser() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        doNothing().when(reviewService).deleteByUUID(any(UUID.class), anyString());

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .with(jwtUser("keycloakId-of-author"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    // ----- Demonstrates that a non-existent reviewUuid surfaces 404 on DELETE too -------

    @Test
    void shouldReturn404WhenDeletingNonExistentReview() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No review with the UUID : " + reviewUuid + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new ReviewNotFoundException("No review with the UUID : " + reviewUuid + " found"))
                .when(reviewService).deleteByUUID(any(UUID.class), anyString());

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .with(jwtUser("keycloakId"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // ----- Disabled placeholder for BUG-372: missing @Valid on PathVariable rating types ----
    // Currently no path/query rating exists in this controller; left only as a marker if the
    // contract grows.

    @Test
    @Disabled("BUG-373 — admin moderation contract not yet defined; pin enabled when the product " +
            "decision lands. Today admins follow the same author-only path as users.")
    void shouldAllowAdminToDeleteAnotherUsersReview_BUG_373_desiredBehaviour() throws Exception {
        UUID reviewUuid = UUID.randomUUID();
        doNothing().when(reviewService).deleteByUUID(any(UUID.class), anyString());

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUuid)
                        .with(jwtAdmin("admin-keycloak-id"))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
