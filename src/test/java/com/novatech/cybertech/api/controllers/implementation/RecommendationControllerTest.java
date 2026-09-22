package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.services.core.RecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_RECOMMENDATION_COUNT;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link RecommendationController}. Mirrors
 * {@code WishlistManagementControllerTest}'s shape ({@code @WebMvcTest} + {@code TestSecurityConfig}
 * + {@code @MockitoBean} + {@code JwtTestUtils}).
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = RecommendationController.class)
class RecommendationControllerTest {

    private static final String GET_MY_RECOMMENDATIONS_ENDPOINT = "/api/v1/services/recommendations/my-recommendations";
    private static final String KEYCLOAK_ID = "keycloakId";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendationService recommendationService;

    @Test
    void shouldGetMyRecommendationsSuccessfully() throws Exception {
        final List<ProductResponseDto> recommendations = List.of(ProductResponseDto.builder().uuid("p1").build());
        when(recommendationService.getRecommendedProducts(anyString(), anyInt())).thenReturn(recommendations);

        mockMvc.perform(get(GET_MY_RECOMMENDATIONS_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(recommendations), STRICT));
    }

    @Test
    void shouldGetMyRecommendations_forwardsJwtSubjectAndDefaultCount() throws Exception {
        when(recommendationService.getRecommendedProducts(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get(GET_MY_RECOMMENDATIONS_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());

        final ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(recommendationService).getRecommendedProducts(subjectCaptor.capture(), countCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo(KEYCLOAK_ID);
        assertThat(countCaptor.getValue()).isEqualTo(DEFAULT_RECOMMENDATION_COUNT);
    }

    @Test
    void shouldGetMyRecommendations_forwardsExplicitCount() throws Exception {
        when(recommendationService.getRecommendedProducts(eq(KEYCLOAK_ID), eq(5))).thenReturn(List.of());

        mockMvc.perform(get(GET_MY_RECOMMENDATIONS_ENDPOINT)
                        .param("n", "5")
                        .with(JwtTestUtils.jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendedProducts(KEYCLOAK_ID, 5);
    }

    @Test
    void whenAnonymousGetMyRecommendations_thenUnauthorized() throws Exception {
        mockMvc.perform(get(GET_MY_RECOMMENDATIONS_ENDPOINT)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenAdminAuthenticated_thenAllowedOnIsAuthenticatedEndpoint() throws Exception {
        // @PreAuthorize("isAuthenticated()") accepts ROLE_ADMIN as well.
        when(recommendationService.getRecommendedProducts(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get(GET_MY_RECOMMENDATIONS_ENDPOINT)
                        .with(JwtTestUtils.jwtAdmin("adminId"))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
