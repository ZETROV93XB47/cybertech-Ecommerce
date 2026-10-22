package com.novatech.cybertech.integration.product;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.ProductSearchSort;
import com.novatech.cybertech.events.ProductReviewsChangedEvent;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalog sort ({@code POST /product/search}, field {@code sort}) on a small indexed catalog, created
 * through the admin API so the real indexing path runs. Ratings come from reviews saved in MySQL and
 * re-indexed by the {@link ProductReviewsChangedEvent} listener, as after a review is posted.
 */
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ProductSearchSortFlowIT — catalog sort")
class ProductSearchSortFlowIT {

    private static final String CREATE_ENDPOINT = "/api/v1/services/admin/management/product/create";
    private static final String SEARCH_ENDPOINT = "/api/v1/services/product/search";
    private static final String FACETS_ENDPOINT = "/api/v1/services/product/search/facets";
    private static final String ADMIN_KC = "kc-admin-sort-it";
    private static final Duration AWAIT_AT_MOST = Duration.ofSeconds(10);
    private static final Duration AWAIT_POLL = Duration.ofMillis(100);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductSearchRepository productSearchRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ElasticsearchOperations elasticsearchOperations;
    @Autowired private TestDataCleaner testDataCleaner;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationEventPublisher eventPublisher;

    private UUID dell;
    private UUID hp;
    private UUID acer;
    private UUID ecran;
    private UUID samsung;

    @BeforeEach
    void seed() throws Exception {
        testDataCleaner.wipe();
        final IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        // Dell and acer share a price: their order is decided by the uuid tie-break.
        dell = createProduct(computer("DELL XPS 16", Brand.DELL, "1000.00"));
        hp = createProduct(computer("HP Omen 32", Brand.HP, "2000.00"));
        acer = createProduct(computer("acer Nitro 5", Brand.ACER, "1000.00"));
        ecran = createProduct(monitor("Écran AOC 24", Brand.AOC, "150.00"));
        samsung = createProduct(monitor("Samsung Odyssey", Brand.SAMSUNG, "400.00"));

        indexOps.refresh();
        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST)
                .untilAsserted(() -> assertThat(productSearchRepository.count()).isEqualTo(5L));
    }

    @AfterEach
    void tearDown() {
        try {
            final IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
            }
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    @Test
    @DisplayName("NAME_ASC: A → Z ignoring case and accents (« Écran » with the E)")
    void nameAsc() throws Exception {
        assertThat(searchUuids(ProductSearchSort.NAME_ASC, 0, 20)).containsExactly(acer, dell, ecran, hp, samsung);
    }

    @Test
    @DisplayName("PRICE_ASC / PRICE_DESC: by price, equal prices ordered by uuid")
    void price() throws Exception {
        final List<UUID> samePrice = Stream.of(dell, acer).sorted((a, b) -> a.toString().compareTo(b.toString())).toList();

        assertThat(searchUuids(ProductSearchSort.PRICE_ASC, 0, 20))
                .containsExactly(ecran, samsung, samePrice.get(0), samePrice.get(1), hp);
        assertThat(searchUuids(ProductSearchSort.PRICE_DESC, 0, 20))
                .containsExactly(hp, samePrice.get(0), samePrice.get(1), samsung, ecran);
    }

    @Test
    @DisplayName("pagination is stable: pages of 2 concatenate to the full sorted list, without duplicates")
    void stablePagination() throws Exception {
        final List<UUID> full = searchUuids(ProductSearchSort.PRICE_ASC, 0, 20);
        final List<UUID> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            paged.addAll(searchUuids(ProductSearchSort.PRICE_ASC, page, 2));
        }

        assertThat(paged).containsExactlyElementsOf(full).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    @DisplayName("RATING_DESC: best average first, then most reviews; hidden reviews ignored; unrated products last")
    void ratingDesc() throws Exception {
        final UserEntity author = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId("kc-author-" + UUID.randomUUID()).email("author+" + UUID.randomUUID() + "@example.com").build());
        saveReviews(samsung, author, 5, 5);            // 5.0, 2 reviews
        saveReviews(dell, author, 5);                  // 5.0, 1 review
        saveReviews(hp, author, 5, 4);                 // 4.5, 2 reviews
        saveHiddenReview(hp, author, 1);               // hateful: not published, not counted
        Stream.of(samsung, dell, hp).forEach(this::reviewsChanged);

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            elasticsearchOperations.indexOps(ProductDocument.class).refresh();
            assertThat(productSearchRepository.findById(hp.toString()).orElseThrow().getReviewCount()).isEqualTo(2);
            assertThat(productSearchRepository.findById(dell.toString()).orElseThrow().getAverageRating()).isEqualTo(5.0);
            assertThat(productSearchRepository.findById(samsung.toString()).orElseThrow().getReviewCount()).isEqualTo(2);
        });
        final ProductDocument hpDocument = productSearchRepository.findById(hp.toString()).orElseThrow();
        assertThat(hpDocument.getAverageRating()).isEqualTo(4.5);
        assertThat(productSearchRepository.findById(acer.toString()).orElseThrow().getAverageRating()).isNull();
        assertThat(productSearchRepository.findById(acer.toString()).orElseThrow().getReviewCount()).isZero();

        final List<UUID> unrated = Stream.of(acer, ecran).sorted((a, b) -> a.toString().compareTo(b.toString())).toList();
        assertThat(searchUuids(ProductSearchSort.RATING_DESC, 0, 20))
                .containsExactly(samsung, dell, hp, unrated.get(0), unrated.get(1));
    }

    @Test
    @DisplayName("no sort = RELEVANCE (score, then uuid): unchanged order for a request without sort")
    void absentSortIsRelevance() throws Exception {
        final List<UUID> absent = searchUuids(null, 0, 20);

        assertThat(absent).isEqualTo(searchUuids(ProductSearchSort.RELEVANCE, 0, 20));
        // No keyword: every product scores the same, the uuid tie-break decides.
        assertThat(absent).isSortedAccordingTo((a, b) -> a.toString().compareTo(b.toString()));
    }

    @Test
    @DisplayName("an unknown sort value is a 400 MALFORMED_JSON, like any unknown enum value")
    void unknownSortIs400() throws Exception {
        mockMvc.perform(post(SEARCH_ENDPOINT)
                        .with(JwtTestUtils.jwtUser("kc-user-sort-it"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sort\":\"POPULARITY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_JSON"));
    }

    @Test
    @DisplayName("the sort does not change the facets")
    void facetsIgnoreSort() throws Exception {
        final String withoutSort = facets(ProductSearchRequestDto.builder().category("COMPUTER").build());
        final String withSort = facets(ProductSearchRequestDto.builder().category("COMPUTER").sort(ProductSearchSort.PRICE_DESC).build());

        assertThat(withSort).isEqualTo(withoutSort);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveReviews(final UUID productUuid, final UserEntity author, final int... ratings) {
        for (final int rating : ratings) {
            saveReview(productUuid, author, rating, false);
        }
    }

    private void saveHiddenReview(final UUID productUuid, final UserEntity author, final int rating) {
        saveReview(productUuid, author, rating, true);
    }

    private void saveReview(final UUID productUuid, final UserEntity author, final int rating, final boolean hateful) {
        // One transaction: ReviewEntity cascades PERSIST to its product, which must still be managed.
        transactionTemplate.executeWithoutResult(tx -> reviewRepository.save(ReviewEntity.builder()
                .uuid(UUID.randomUUID())
                .productEntity(productRepository.findByUuid(productUuid).orElseThrow())
                .userEntity(userRepository.findById(author.getId()).orElseThrow())
                .rating(rating)
                .isHateful(hateful)
                .build()));
    }

    /** What ReviewManagementServiceImp publishes after a review change: handled after commit. */
    private void reviewsChanged(final UUID productUuid) {
        transactionTemplate.executeWithoutResult(tx -> eventPublisher.publishEvent(new ProductReviewsChangedEvent(this, productUuid)));
    }

    private List<UUID> searchUuids(final ProductSearchSort sort, final int page, final int size) throws Exception {
        final ProductSearchRequestDto req = ProductSearchRequestDto.builder().page(page).size(size).sort(sort).build();
        final MvcResult result = mockMvc.perform(post(SEARCH_ENDPOINT)
                        .with(JwtTestUtils.jwtUser("kc-user-sort-it"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        final Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
        return ((List<?>) body.get("content")).stream()
                .map(item -> UUID.fromString(String.valueOf(((Map<?, ?>) item).get("uuid"))))
                .toList();
    }

    private String facets(final ProductSearchRequestDto req) throws Exception {
        return mockMvc.perform(post(FACETS_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID createProduct(final ProductCreateRequestDto dto) throws Exception {
        final MvcResult res = mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(JwtTestUtils.jwtAdmin(ADMIN_KC))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        final Map<String, Object> body = objectMapper.readValue(res.getResponse().getContentAsString(), new TypeReference<>() {
        });
        return UUID.fromString((String) body.get("uuid"));
    }

    private static ProductCreateRequestDto computer(final String name, final Brand brand, final String price) {
        final Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("cpu", "Intel i7");
        attrs.put("gpu", "NVIDIA");
        attrs.put("ram", 16);
        attrs.put("os", "Windows 11");
        attrs.put("connectivity", "Wi-Fi 6");
        attrs.put("displayType", "OLED");
        attrs.put("memory", 512);
        return ProductCreateRequestDto.builder()
                .name(name).price(new BigDecimal(price)).brand(brand).category("COMPUTER")
                .stock(10).description(name + " — laptop").attributes(attrs)
                .build();
    }

    private static ProductCreateRequestDto monitor(final String name, final Brand brand, final String price) {
        final Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("resolution", "1920x1080");
        attrs.put("refreshRate", 144);
        return ProductCreateRequestDto.builder()
                .name(name).price(new BigDecimal(price)).brand(brand).category("MONITOR")
                .stock(5).description(name + " — monitor").attributes(attrs)
                .build();
    }
}
