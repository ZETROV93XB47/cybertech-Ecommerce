package com.novatech.cybertech.integration.bankcard;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The caller's single bank card against real containers: delete really removes the row, a
 * second add is a 409, and replace swaps the card atomically.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("BankCardFlowIT — /bank-card add / delete / replace against real containers")
class BankCardFlowIT {

    private static final String ADD = "/api/v1/services/bank-card/add";
    private static final String GET = "/api/v1/services/bank-card/get";
    private static final String DELETE = "/api/v1/services/bank-card/delete";
    private static final String REPLACE = "/api/v1/services/bank-card/replace";
    private static final String FIRST_PAN = "4242424242424242";
    private static final String SECOND_PAN = "1111111111115678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankCardRepository bankCardRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    private String keycloakId;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        keycloakId = "kc-it-" + UUID.randomUUID();
        userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId(keycloakId)
                .email("card-it+" + UUID.randomUUID() + "@example.com")
                .bankCardEntity(null)
                .build());
    }

    @Test
    @DisplayName("add → delete → get is 404 → add a new card is 201")
    void deleteReallyRemovesTheCard() throws Exception {
        perform(post(ADD), card(FIRST_PAN)).andExpect(status().isCreated());

        mockMvc.perform(delete(DELETE).with(jwtUser(keycloakId)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(GET).with(jwtUser(keycloakId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BANK_CARD_NOT_FOUND"));
        assertThat(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).isEmpty();

        perform(post(ADD), card(SECOND_PAN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.endsWith("5678")));
    }

    @Test
    @DisplayName("add on an existing card is 409 BANK_CARD_ALREADY_EXISTS, the card is unchanged")
    void secondAddIsConflict() throws Exception {
        perform(post(ADD), card(FIRST_PAN)).andExpect(status().isCreated());

        perform(post(ADD), card(SECOND_PAN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BANK_CARD_ALREADY_EXISTS"));

        mockMvc.perform(get(GET).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.endsWith("4242")));
    }

    @Test
    @DisplayName("replace is 200, the old card is gone and no full PAN reaches the logs or the body")
    void replaceSwapsTheCard(final CapturedOutput output) throws Exception {
        perform(post(ADD), card(FIRST_PAN)).andExpect(status().isCreated());
        final UUID oldUuid = bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId).getFirst().getUuid();

        final String body = perform(put(REPLACE), card(SECOND_PAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.endsWith("5678")))
                .andReturn().getResponse().getContentAsString();

        assertThat(bankCardRepository.findByUuid(oldUuid)).isEmpty();
        assertThat(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).hasSize(1);
        assertThat(body).doesNotContain(SECOND_PAN);
        assertThat(output.getAll()).doesNotContain(FIRST_PAN).doesNotContain(SECOND_PAN);
    }

    @Test
    @DisplayName("replace with an invalid number is 400 and the current card is kept")
    void invalidReplaceKeepsTheCard() throws Exception {
        perform(post(ADD), card(FIRST_PAN)).andExpect(status().isCreated());

        perform(put(REPLACE), card("12AB"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(GET).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.endsWith("4242")));
    }

    @Test
    @DisplayName("replace with an expired card is 400 BANK_CARD_EXPIRED and the current card is kept")
    void expiredReplaceKeepsTheCard() throws Exception {
        perform(post(ADD), card(FIRST_PAN)).andExpect(status().isCreated());

        perform(put(REPLACE), UserDtoFixtures.aValidBankCardCreationRequestBuilder().cardNumber(SECOND_PAN).expiryDate("01/2020").build())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BANK_CARD_EXPIRED"));

        mockMvc.perform(get(GET).with(jwtUser(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.endsWith("4242")));
    }

    private static BankCardCreationRequestDto card(final String pan) {
        return UserDtoFixtures.aValidBankCardCreationRequestBuilder().cardNumber(pan).build();
    }

    private ResultActions perform(final org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  final BankCardCreationRequestDto body) throws Exception {
        return mockMvc.perform(request
                .with(jwtUser(keycloakId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
