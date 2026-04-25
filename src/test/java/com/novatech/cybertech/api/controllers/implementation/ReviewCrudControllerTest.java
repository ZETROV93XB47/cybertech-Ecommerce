package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.exceptions.ReviewNotFoundException;
import com.novatech.cybertech.exceptions.UserNotAuthorOfReviewException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.services.implementation.ReviewManagementServiceImp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ReviewCrudController.class)
class ReviewCrudControllerTest {

    private static final String CREATE_REVIEW_ENDPOINT = "/api/v1/services/review/create";
    private static final String UPDATE_REVIEW_ENDPOINT = "/api/v1/services/review/update/{reviewUuid}";
    private static final String GET_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/get/{reviewUuid}";
    private static final String DELETE_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/delete/{reviewUuid}";
    private static final String REVIEWABLE_PRODUCTS_ENDPOINT = "/api/v1/services/review/reviewable";


    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReviewManagementServiceImp reviewService;


    @Test
    void shouldGetReviewByUuidSuccessfully() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        ReviewResponseDto reviewResponseDto = ReviewResponseDto.builder()
                .uuid(reviewUUID)
                .build();

        when(reviewService.getByUUID(reviewUUID)).thenReturn(reviewResponseDto);

        mockMvc.perform(get(GET_REVIEW_BY_UUID_ENDPOINT, reviewUUID)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(reviewResponseDto), STRICT));
    }

    @Test
    void shouldCreateReviewSuccessfully() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        UUID productUUID = UUID.randomUUID();
        UUID orderUuid = UUID.randomUUID();

        ReviewCreateRequestDto reviewCreateRequestDto = ReviewCreateRequestDto.builder()
                // userUuid removed: identity is now derived from the JWT in the controller/service.
                .orderUuid(orderUuid)
                .productUuid(productUUID)
                .rating(5)
                .comment("comment")
                .build();

        ReviewResponseDto reviewResponseDto = ReviewResponseDto.builder()
                .uuid(reviewUUID)
                .build();

        when(reviewService.create(any(ReviewCreateRequestDto.class), any(String.class))).thenReturn(reviewResponseDto);

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))

                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewCreateRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(reviewResponseDto), STRICT));
    }


    @Test
    void shouldFailCreatingReviewCauseDtoBadRequest() throws Exception {
        ReviewCreateRequestDto reviewCreateRequestDto = new ReviewCreateRequestDto();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewCreateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldUpdateReviewSuccessfully() throws Exception {
        UUID reviewUUID = UUID.randomUUID();

        ReviewUpdateRequestDto reviewUpdateRequestDto = ReviewUpdateRequestDto.builder()
                .reviewUuid(reviewUUID)
                .rating(5)
                .comment("comment")
                .build();

        ReviewResponseDto reviewResponseDto = ReviewResponseDto.builder()
                .uuid(reviewUUID)
                .build();

        when(reviewService.update(any(ReviewUpdateRequestDto.class), any(String.class))).thenReturn(reviewResponseDto);

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewUpdateRequestDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(reviewResponseDto), STRICT));
    }

    @Test
    void shouldFailUpdatingReviewCauseDtoBadRequest() throws Exception {
        ReviewUpdateRequestDto reviewUpdateRequestDto = new ReviewUpdateRequestDto();

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewUpdateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailUpdatingReviewCauseUserUpdatingReviewNotFound() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";
        ReviewUpdateRequestDto reviewUpdateRequestDto = ReviewUpdateRequestDto.builder()
                .reviewUuid(reviewUUID)
                .comment("comment")
                .rating(5)
                .build();

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("User that's trying to post this comment doesn't exists or is not active")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(reviewService.update(reviewUpdateRequestDto, keycloakId)).thenThrow(new UserNotFoundException("User that's trying to post this comment doesn't exists or is not active"));

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewUpdateRequestDto)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));

    }

    @Test
    void shouldFailUpdatingReviewCauseUserNotAuthorOfReview() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";
        ReviewUpdateRequestDto reviewUpdateRequestDto = ReviewUpdateRequestDto.builder()
                .reviewUuid(reviewUUID)
                .comment("comment")
                .rating(5)
                .build();

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Current review Doesn't belongs to the connected user")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(reviewService.update(reviewUpdateRequestDto, keycloakId)).thenThrow(new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user"));

        mockMvc.perform(patch(UPDATE_REVIEW_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .content(asJsonString(reviewUpdateRequestDto)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }


    @Test
    void shouldSucceedDeletingReviewByUuid() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";

        doNothing().when(reviewService).deleteByUUID(reviewUUID, keycloakId);

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldFailDeletingReviewByUuidCauseUserNotAuthorOfReview() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Current review Doesn't belongs to the connected user")
                .httpStatusCode(403)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user"))
                .when(reviewService)
                .deleteByUUID(reviewUUID, keycloakId);

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));

        verify(reviewService).deleteByUUID(reviewUUID, keycloakId);
    }

    @Test
    void shouldFailDeletingReviewByUuidCauseUserNotFound() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("User that's trying to delete this comment doesn't exists or is not active")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new UserNotFoundException("User that's trying to delete this comment doesn't exists or is not active"))
                .when(reviewService)
                .deleteByUUID(reviewUUID, keycloakId);

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));

    }

    @Test
    void shouldFailDeletingReviewByUuidCauseReviewNotFoundException() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        String keycloakId = "keycloakId";

        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("No review with the UUID : " + reviewUUID + " found")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new ReviewNotFoundException("No review with the UUID : " + reviewUUID + " found"))
                .when(reviewService)
                .deleteByUUID(reviewUUID, keycloakId);

        mockMvc.perform(delete(DELETE_REVIEW_BY_UUID_ENDPOINT, reviewUUID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(jwt -> jwt.subject("keycloakId")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    // -------- /reviewable (#4 Option B) --------

    @Test
    void reviewable_authenticatedUser_returnsList() throws Exception {
        UUID p1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        com.novatech.cybertech.dto.response.review.ReviewableProductDto entry =
                com.novatech.cybertech.dto.response.review.ReviewableProductDto.builder()
                        .productUuid(p1).orderUuid(o1).productName("X1").build();
        when(reviewService.getReviewableProducts(any(String.class))).thenReturn(java.util.List.of(entry));

        mockMvc.perform(get(REVIEWABLE_PRODUCTS_ENDPOINT)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                .jwt(j -> j.subject("kc-1")))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].productUuid").value(p1.toString()))
                .andExpect(jsonPath("$[0].orderUuid").value(o1.toString()))
                .andExpect(jsonPath("$[0].productName").value("X1"));
    }

    @Test
    void reviewable_anonymous_returns401() throws Exception {
        mockMvc.perform(get(REVIEWABLE_PRODUCTS_ENDPOINT).accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}