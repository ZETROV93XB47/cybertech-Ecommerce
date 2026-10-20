package com.novatech.cybertech.integration.review;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /review/mine}: only the caller's reviews, paginated, sortable, authenticated. */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("MyReviewsFlowIT — the caller's own reviews")
class MyReviewsFlowIT {

    private static final String MY_REVIEWS_ENDPOINT = "/api/v1/services/review/mine";
    private static final LocalDateTime DAY_ONE = LocalDateTime.of(2026, 10, 1, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String myKeycloakId;
    private UUID laptopUuid;
    private UUID orderUuid;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        myKeycloakId = "kc-me-" + UUID.randomUUID();
        orderUuid = UUID.randomUUID();
        // One transaction: ReviewEntity cascades PERSIST to its product, which must still be managed.
        transactionTemplate.executeWithoutResult(status -> seedReviews());
    }

    private void seedReviews() {
        final ProductEntity laptop = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Laptop").build());
        final ProductEntity screen = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Écran").build());
        final ProductEntity mouse = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Souris").build());
        laptopUuid = laptop.getUuid();

        final UserEntity me = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId(myKeycloakId).email("me+" + UUID.randomUUID() + "@example.com").build());
        final UserEntity someoneElse = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId("kc-other-" + UUID.randomUUID()).email("other+" + UUID.randomUUID() + "@example.com").build());

        // Mine, oldest to newest: 5 (with order), 2, 3 (hidden by moderation but still mine).
        saveReview(laptop, me, 5, "Top", false, orderUuid, DAY_ONE);
        saveReview(screen, me, 2, "Bof", false, null, DAY_ONE.plusDays(1));
        saveReview(mouse, me, 3, "Texte modéré", true, null, DAY_ONE.plusDays(2));
        // Someone else's: never listed.
        saveReview(laptop, someoneElse, 1, "Pas le mien", false, null, DAY_ONE.plusDays(3));
    }

    private void saveReview(final ProductEntity reviewed, final UserEntity author, final int rating, final String comment,
                            final boolean hateful, final UUID order, final LocalDateTime createdAt) {
        final ReviewEntity saved = reviewRepository.saveAndFlush(ReviewEntity.builder()
                .uuid(UUID.randomUUID())
                .productEntity(reviewed)
                .userEntity(author)
                .rating(rating)
                .comment(comment)
                .isHateful(hateful)
                .orderUuid(order)
                .build());
        // createdAt is not updatable through JPA: the date the ordering relies on is set in SQL.
        jdbcTemplate.update("UPDATE reviewTable SET createdAt = ? WHERE id = ?", createdAt, saved.getId());
    }

    @Test
    @DisplayName("only the caller's reviews, newest first by default, moderated ones included, with product and order")
    void onlyMineNewestFirst() throws Exception {
        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).with(jwtUser(myKeycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].rating", contains(3, 2, 5)))
                .andExpect(jsonPath("$.content[*].productName", contains("Souris", "Écran", "Laptop")))
                .andExpect(jsonPath("$.content[2].productUuid").value(laptopUuid.toString()))
                .andExpect(jsonPath("$.content[2].orderUuid").value(orderUuid.toString()))
                .andExpect(jsonPath("$.content[2].comment").value("Top"))
                .andExpect(jsonPath("$.content[2].createdAt").value("2026-10-01T10:00:00"));
    }

    @Test
    @DisplayName("pagination: size 2, page 1 holds the oldest review")
    void pagination() throws Exception {
        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).param("page", "1").param("size", "2").with(jwtUser(myKeycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].rating", contains(5)));
    }

    @Test
    @DisplayName("sort=rating,asc is honoured; an unknown sort field falls back to newest first")
    void sorting() throws Exception {
        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).param("sort", "rating,asc").with(jwtUser(myKeycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].rating", contains(2, 3, 5)));

        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).param("sort", "userEntity.email,asc").with(jwtUser(myKeycloakId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].rating", contains(3, 2, 5)));
    }

    @Test
    @DisplayName("a user without reviews gets an empty page")
    void emptyForAnotherUser() throws Exception {
        mockMvc.perform(get(MY_REVIEWS_ENDPOINT).with(jwtUser("kc-nobody-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("no token: 401")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get(MY_REVIEWS_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }
}
