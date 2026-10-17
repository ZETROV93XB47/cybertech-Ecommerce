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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public product reviews end-to-end against real MySQL: GET /review/product/{productUuid} without a
 * JWT. Reviews are seeded straight through the repositories (with fixed dates, and one review
 * flagged hateful, which the create endpoint can never persist) to pin the read contract: rating
 * summary, newest-first pagination, moderation filter, and no personal data beyond a first name.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ProductReviewsFlowIT — public reviews of a product page")
class ProductReviewsFlowIT {

    private static final String PRODUCT_REVIEWS_ENDPOINT = "/api/v1/services/review/product/{productUuid}";
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

    private ProductEntity product;
    private UserEntity camille;

    @BeforeEach
    void seed() {
        testDataCleaner.wipe();
        // One transaction: ReviewEntity cascades PERSIST to its product, which must still be managed.
        transactionTemplate.executeWithoutResult(status -> seedCatalogAndReviews());
    }

    private void seedCatalogAndReviews() {
        product = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Écran test").build());
        final ProductEntity otherProduct = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Autre produit").build());

        camille = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .firstName("Camille").lastName("Durand").email("camille.durand@example.com").build());
        final UserEntity jean = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .firstName("Jean").lastName("Martin").email("jean.martin@example.com").build());
        final UserEntity noFirstName = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .firstName(null).lastName("Anonyme").email("anonyme@example.com").build());
        final UserEntity troll = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .firstName("Troll").lastName("Malveillant").email("troll@example.com").build());

        // Published reviews of the product: 5, 4, 4 -> average 4.3, oldest first.
        saveReview(product, camille, 5, "Parfait, je recommande", false, DAY_ONE);
        saveReview(product, jean, 4, "Très bon écran", false, DAY_ONE.plusDays(1));
        saveReview(product, noFirstName, 4, null, false, DAY_ONE.plusDays(2));
        // Flagged by moderation: never listed, never counted.
        saveReview(product, troll, 1, "Texte haineux", true, DAY_ONE.plusDays(3));
        // Another product's review: never mixed in.
        saveReview(otherProduct, jean, 1, "Pas terrible", false, DAY_ONE.plusDays(4));
    }

    private void saveReview(final ProductEntity reviewed, final UserEntity author, final int rating, final String comment,
                            final boolean hateful, final LocalDateTime createdAt) {
        final ReviewEntity saved = reviewRepository.saveAndFlush(ReviewEntity.builder()
                .uuid(UUID.randomUUID())
                .productEntity(reviewed)
                .userEntity(author)
                .rating(rating)
                .comment(comment)
                .isHateful(hateful)
                .build());
        // JPA auditing (@CreatedDate) stamps "now" and the column is not updatable through JPA:
        // the posting date the ordering relies on is set in SQL.
        jdbcTemplate.update("UPDATE reviewTable SET createdAt = ? WHERE id = ?", createdAt, saved.getId());
    }

    @Test
    @DisplayName("anonymous caller: average, count and distribution over published reviews only, newest first")
    void summaryAndFirstPage() throws Exception {
        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, product.getUuid()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(jsonPath("$.averageRating").value(4.3))
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.distribution.1").value(0))
                .andExpect(jsonPath("$.distribution.2").value(0))
                .andExpect(jsonPath("$.distribution.3").value(0))
                .andExpect(jsonPath("$.distribution.4").value(2))
                .andExpect(jsonPath("$.distribution.5").value(1))
                .andExpect(jsonPath("$.reviews.page.size").value(10))
                .andExpect(jsonPath("$.reviews.page.totalElements").value(3))
                .andExpect(jsonPath("$.reviews.content[*].rating", contains(4, 4, 5)))
                .andExpect(jsonPath("$.reviews.content[0].authorFirstName").doesNotExist())
                .andExpect(jsonPath("$.reviews.content[1].authorFirstName").value("Jean"))
                .andExpect(jsonPath("$.reviews.content[2].authorFirstName").value("Camille"))
                .andExpect(jsonPath("$.reviews.content[2].comment").value("Parfait, je recommande"))
                .andExpect(jsonPath("$.reviews.content[2].createdAt").value("2026-10-01"));
    }

    @Test
    @DisplayName("pagination: size 2 splits the 3 published reviews over 2 pages, a sort parameter is ignored")
    void pagination() throws Exception {
        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, product.getUuid()).param("size", "2").param("sort", "rating,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.page.totalPages").value(2))
                .andExpect(jsonPath("$.reviews.content[*].createdAt", contains("2026-10-03", "2026-10-02")));

        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, product.getUuid()).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.page.number").value(1))
                .andExpect(jsonPath("$.reviews.content[*].authorFirstName", contains("Camille")))
                // The summary always covers every published review, whatever the page.
                .andExpect(jsonPath("$.reviewCount").value(3));
    }

    @Test
    @DisplayName("no personal data beyond the first name, and nothing from the review flagged hateful")
    void noPersonalDataNoHatefulReview() throws Exception {
        final String body = mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, product.getUuid()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("Durand", "Martin", "Anonyme", "@example.com", camille.getUuid().toString(), camille.getKeycloakId())
                .doesNotContain("Troll", "Texte haineux", "Pas terrible")
                .doesNotContain("email", "lastName", "userUuid", "keycloakId");
    }

    @Test
    @DisplayName("a product without published reviews: null average, every rating at 0")
    void productWithoutReviews() throws Exception {
        final ProductEntity unreviewed = productRepository.save(ProductEntityBuilder.aValidProductBuilder().name("Sans avis").build());

        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, unreviewed.getUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").doesNotExist())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.distribution.5").value(0))
                .andExpect(jsonPath("$.reviews.content").isEmpty());
    }

    @Test
    @DisplayName("unknown product: 404 PRODUCT_NOT_FOUND")
    void unknownProduct() throws Exception {
        mockMvc.perform(get(PRODUCT_REVIEWS_ENDPOINT, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }
}
