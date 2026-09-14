package com.novatech.cybertech.api.controllers.implementation.product;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.ProductCategorySchemaController;
import com.novatech.cybertech.dto.response.product.ProductCategorySchemaResponseDto;
import com.novatech.cybertech.exceptions.UnknownProductCategoryException;
import com.novatech.cybertech.services.core.ProductCategorySchemaService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAnonymous;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public (non-admin) read access — {@code /api/v1/services/product-category-schemas/**} is
 * whitelisted in {@code SecurityConfig#PUBLIC_URLS} (mirrored in {@link TestSecurityConfig}), so
 * every request here is anonymous.
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = ProductCategorySchemaController.class)
class ProductCategorySchemaControllerTest {

    private static final String GET_BY_KEY_ENDPOINT = "/api/v1/services/product-category-schemas/{categoryKey}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductCategorySchemaService productCategorySchemaService;

    @Test
    void shouldGetByCategoryKeyAsAnonymous() throws Exception {
        ProductCategorySchemaResponseDto response = ProductCategorySchemaResponseDto.builder()
                .categoryKey("COMPUTER")
                .label("Computers")
                .jsonSchema("{\"type\":\"object\"}")
                .active(true)
                .build();
        when(productCategorySchemaService.getByCategoryKey("COMPUTER")).thenReturn(response);

        mockMvc.perform(get(GET_BY_KEY_ENDPOINT, "COMPUTER")
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailWhenCategoryUnknown() throws Exception {
        when(productCategorySchemaService.getByCategoryKey("TABLET"))
                .thenThrow(new UnknownProductCategoryException("Unknown product category: TABLET"));

        mockMvc.perform(get(GET_BY_KEY_ENDPOINT, "TABLET")
                        .with(jwtAnonymous())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
