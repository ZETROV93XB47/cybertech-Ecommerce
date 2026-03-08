package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.services.implementation.ReviewManagementServiceImp;
import com.novatech.cybertech.utils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ReviewCrudController.class)
class ReviewCrudControllerTest {

    private static final String CREATE_REVIEW_ENDPOINT = "/api/v1/services/review/create";
    private static final String UPDATE_REVIEW_ENDPOINT = "/api/v1/services/review/update/{reviewUuid}";
    private static final String GET_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/get/{reviewUuid}";
    private static final String DELETE_REVIEW_BY_UUID_ENDPOINT = "/api/v1/services/review/delete/{reviewUuid}";


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
                .andExpect(jsonPath("$.uuid").value(reviewUUID.toString()));
    }

    @Test
    void shouldCreateReviewSuccessfully() throws Exception {
        UUID reviewUUID = UUID.randomUUID();
        UUID productUUID = UUID.randomUUID();
        UUID orderUuid = UUID.randomUUID();

        ReviewCreateRequestDto reviewCreateRequestDto = ReviewCreateRequestDto.builder()
                .userUuid(reviewUUID)
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
                        .content(TestUtils.asJsonString(reviewCreateRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(TestUtils.asJsonString(reviewResponseDto), true));
    }


    @Test
    void shouldFailCreatingReviewCauseDtoBadRequest() throws Exception {
        ReviewCreateRequestDto reviewCreateRequestDto = new ReviewCreateRequestDto(); //.builder().build();

        mockMvc.perform(post(CREATE_REVIEW_ENDPOINT)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .with(csrf())
                        .accept(APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(reviewCreateRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"message\":\"Invalid Request or Request Poorly Constructed\",\"httpStatusCode\":400,\"errorCodeType\":\"TECHNICAL\"}"));

    }

    @Test
    void updateReview() {
    }

    @Test
    void deleteReviewByUuid() {
    }

}