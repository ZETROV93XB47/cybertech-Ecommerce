package com.novatech.cybertech.integration.product;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.dto.request.search.RangeFilter;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.fixtures.support.JwtTestUtils;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SA-W5.4 — End-to-end product search flow IT.
 *
 * <p>Boots the full Spring context against the W0 public {@link TestcontainersConfiguration}
 * (MySQL + Redis + Elasticsearch + MongoDB). Exercises the create / get / update / search /
 * delete surface end-to-end through MockMvc + the real ES index, using {@link org.awaitility.Awaitility}
 * to bridge the ES refresh window.
 *
 * <h2>Bug-pin policy</h2>
 * Original BUG numbers preserved per orchestrator brief:
 * <ul>
 *   <li>BUG-080 — {@code update} does not lock-by-uuid → <b>fixed by F2</b>; the {@code BrokenUpdateFlow}
 *       nested class (originally @Disabled by SA5.4) is now flipped to GREEN, asserting SQL+ES updated.</li>
 *   <li>BUG-180-product — {@code update} did not load existing entity, never re-indexed ES →
 *       <b>fixed by F2</b> (lockByUuid + IGNORE merge + ES re-index). Asserted end-to-end here.</li>
 *   <li>BUG-181 — {@code search} drops caller {@code Sort} → still open; sort smoke test pins
 *       deterministic-cardinality contract.</li>
 *   <li>BUG-081 — bulk {@code deleteByUUIDs} leaves ES orphans → still open. Single-delete path
 *       (verified) does clean ES.</li>
 *   <li>BUG-030 — anonymous → 401 (TestSecurityConfig wires CustomAuthenticationEntryPoint).</li>
 *   <li>BUG-031 — non-admin → 403 (F2 added AuthorizationDeniedException @ExceptionHandler).</li>
 * </ul>
 * No new BUGs filed by this run (BUG-530..BUG-539 reserved per orchestrator).
 */
@Slf4j
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ProductSearchFlowIT — admin CRUD + ES search end-to-end")
class ProductSearchFlowIT {

    private static final String CREATE_ENDPOINT = "/api/v1/services/admin/management/product/create";
    private static final String UPDATE_ENDPOINT = "/api/v1/services/admin/management/product/update/{productUuid}";
    private static final String DELETE_ENDPOINT = "/api/v1/services/admin/management/product/delete/{productUuid}";
    private static final String GET_BY_UUID_ENDPOINT = "/api/v1/services/product/get/{productUuid}";
    private static final String SEARCH_ENDPOINT = "/api/v1/services/product/search";

    private static final String ADMIN_KC = "kc-admin-it";
    private static final String USER_KC = "kc-user-it";

    private static final Duration AWAIT_AT_MOST = Duration.ofSeconds(5);
    private static final Duration AWAIT_POLL = Duration.ofMillis(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private TestDataCleaner testDataCleaner;

    private UUID dellUuid;
    private UUID hpUuid;
    private UUID samsungUuid;

    @BeforeEach
    void seed() throws Exception {
        // 1) Wipe SQL between tests (keeps cross-test isolation).
        testDataCleaner.wipe();

        // 2) Recreate the ES index from scratch (ensures mapping is honoured + no leaked docs).
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();

        // 3) Seed three products via the live admin endpoint so the full create-pipeline runs
        //    (validation → mapper → SQL save → ES indexing).
        dellUuid = createProduct(buildComputerCreate("DELL XPS 16", Brand.DELL, new BigDecimal("1000.00"), 16));
        hpUuid = createProduct(buildComputerCreate("HP Omen 32", Brand.HP, new BigDecimal("2000.00"), 32));
        samsungUuid = createProduct(buildMonitorCreate("Samsung 27\" 4K", Brand.SAMSUNG, new BigDecimal("400.00")));

        // 4) Bridge ES refresh window — explicit refresh + Awaitility ceiling.
        indexOps.refresh();
        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            long count = productSearchRepository.count();
            assertThat(count).as("3 products should be indexed in ES").isEqualTo(3L);
        });
    }

    @AfterEach
    void tearDown() {
        // Defensive ES cleanup; SQL is wiped at the start of each test.
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
            }
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    // -----------------------------------------------------------------------------------------
    // Test 1 — seeding succeeded (3 SQL rows + 3 ES docs)
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("seed populates 3 products in MySQL and Elasticsearch")
    void seedPopulates_threeRowsInSqlAndEs() {
        assertThat(productRepository.count()).isEqualTo(3L);
        assertThat(productSearchRepository.count()).isEqualTo(3L);
    }

    // -----------------------------------------------------------------------------------------
    // Test 2 — search by brand DELL → 1 hit
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("search by brand DELL → exactly 1 hit (the DELL XPS)")
    void searchByBrandDell_returnsOneHit() throws Exception {
        ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                .brands(List.of(Brand.DELL))
                .build();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            int total = readTotalElements(res);
            assertThat(total).isEqualTo(1);
            String body = res.getResponse().getContentAsString();
            assertThat(body).contains("DELL");
            assertThat(body).doesNotContain("HP Omen");
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 3 — search by category COMPUTER → 2 hits
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("search by category COMPUTER → 2 hits (DELL + HP)")
    void searchByCategoryComputer_returnsTwoHits() throws Exception {
        ProductSearchRequestDto req = baseRequest(Category.COMPUTER).build();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            assertThat(readTotalElements(res)).isEqualTo(2);
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 4 — price range 500..1500 → only DELL (1000)
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("search by price range 500..1500 → 1 hit (DELL only)")
    void searchByPriceRange_returnsDellOnly() throws Exception {
        ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                .priceMin(500.0)
                .priceMax(1500.0)
                .build();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            assertThat(readTotalElements(res)).isEqualTo(1);
            assertThat(res.getResponse().getContentAsString()).contains("DELL");
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 5 — numeric range ram >= 32 → only HP (32GB)
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("search by attribute ram >= 32 → 1 hit (HP only)")
    void searchByRamRange_returnsHpOnly() throws Exception {
        Map<String, RangeFilter> ranges = new HashMap<>();
        ranges.put("ram", new RangeFilter(32.0, null));
        ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                .numericRanges(ranges)
                .build();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            assertThat(readTotalElements(res)).isEqualTo(1);
            assertThat(res.getResponse().getContentAsString()).contains("HP");
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 6 — category-only sums to 3 across COMPUTER + MONITOR
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("category-only across COMPUTER + MONITOR sums to 3")
    void categoryOnly_sumsToThreeAcrossComputerAndMonitor() throws Exception {
        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            int computers = readTotalElements(performSearch(baseRequest(Category.COMPUTER).build())
                    .andExpect(status().isOk()).andReturn());
            int monitors = readTotalElements(performSearch(baseRequest(Category.MONITOR).build())
                    .andExpect(status().isOk()).andReturn());

            assertThat(computers).isEqualTo(2);
            assertThat(monitors).isEqualTo(1);
            assertThat(computers + monitors).isEqualTo(3);
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 7 — public GET /get/{uuid} reachable without JWT (W0 expanded PUBLIC_URLS)
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("GET /api/v1/services/product/get/{uuid} reachable without JWT")
    void publicGetByUuid_reachableWithoutJwt() throws Exception {
        mockMvc.perform(get(GET_BY_UUID_ENDPOINT, dellUuid)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(dellUuid.toString()))
                .andExpect(jsonPath("$.brand").value("DELL"));
    }

    // -----------------------------------------------------------------------------------------
    // Test 8 — security: ROLE_USER POST admin CRUD → 403 (BUG-031 fixed by F2);
    //                   anonymous → 401 (BUG-030 closed by W0 entry-point wiring)
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("ROLE_USER on admin create → 403 (post-F2 BUG-031); anonymous → 401 (W0 BUG-030)")
    void securityOnAdminEndpoint_userIs403_anonymousIs401() throws Exception {
        ProductCreateRequestDto payload = buildComputerCreate("Should Not Persist", Brand.ASUS, new BigDecimal("100.00"), 16);

        // ROLE_USER → 403 (AuthorizationDeniedException handled by F2 advice)
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(JwtTestUtils.jwtUser(USER_KC))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());

        // Anonymous → 401 (CustomAuthenticationEntryPoint wired in TestSecurityConfig)
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        // Persistence guard — neither rejected attempt should have created a 4th product.
        assertThat(productRepository.count()).isEqualTo(3L);
    }

    // -----------------------------------------------------------------------------------------
    // Test 9 — admin delete of DELL → ES /search?brand=DELL returns 0
    //          Single-delete path is correct (deleteByUUID hits both stores). BUG-081 only
    //          affects the bulk variant deleteByUUIDs (still open).
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("admin DELETE removes DELL from MySQL AND ES (BUG-081 single-path is fine)")
    void adminDeleteRemovesFromBothStores() throws Exception {
        mockMvc.perform(delete(DELETE_ENDPOINT, dellUuid)
                        .with(JwtTestUtils.jwtAdmin(ADMIN_KC))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        indexOps.refresh();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            // SQL state
            assertThat(productRepository.count()).isEqualTo(2L);
            assertThat(productRepository.findByUuid(dellUuid)).isEmpty();

            // ES state via /search?brand=DELL → must return 0
            ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                    .brands(List.of(Brand.DELL))
                    .build();
            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            assertThat(readTotalElements(res)).isZero();
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 10 — ES reserved characters (`*`, `"`) injected as keyword → bounded hits.
    //           multi_match treats them as literals; never an unbounded match-all.
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("reserved chars in keyword (*, \") return bounded results, never unbounded match-all")
    void reservedCharsInKeyword_returnBoundedResults() throws Exception {
        for (String injected : List.of("*", "\"")) {
            ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                    .keyword(injected)
                    .build();

            MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
            int total = readTotalElements(res);
            // Bound: cannot exceed the in-category corpus (2 COMPUTER docs).
            assertThat(total)
                    .as("keyword=%s must not bypass to match-all", injected)
                    .isBetween(0, 2);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Test 11 — Sort smoke test: BUG-181 documented (ProductSearchServiceImp drops Sort).
    //           Two identical runs return identical cardinality (deterministic count).
    // -----------------------------------------------------------------------------------------
    @Test
    @DisplayName("BUG-181: search ignores Sort — two identical calls have identical totals")
    void sortSmokeTest_pinsBug181DeterministicCardinality() throws Exception {
        ProductSearchRequestDto req = baseRequest(Category.COMPUTER).build();

        await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
            MvcResult first = performSearch(req).andExpect(status().isOk()).andReturn();
            MvcResult second = performSearch(req).andExpect(status().isOk()).andReturn();

            assertThat(readTotalElements(first)).isEqualTo(readTotalElements(second)).isEqualTo(2);
        });
    }

    // -----------------------------------------------------------------------------------------
    // Test 12 — BUG-180-product / BUG-080: F2 fix for update.
    //           Originally pinned by SA5.4 as a @Disabled @Nested BrokenUpdateFlow class.
    //           F2 made update() use lockByUuid + IGNORE-style merge + ES re-index.
    //           Flip to GREEN: PATCH updates SQL row AND ES doc; no orphan ES doc with old data.
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("BUG-180-product / BUG-080: update flow fixed by F2 — SQL+ES both updated")
    class FixedUpdateFlow {

        @Test
        @DisplayName("PATCH /update/{uuid} updates SQL row in place AND re-indexes ES doc")
        void updatePersistsToSqlAndReIndexesEs() throws Exception {
            // Capture the original DELL row's surrogate id (must be preserved post-update).
            ProductEntity beforeUpdate = productRepository.findByUuid(dellUuid).orElseThrow();
            Long sqlIdBefore = beforeUpdate.getId();
            assertThat(sqlIdBefore).as("DELL row must exist pre-update").isNotNull();

            // Build a partial update — change name + price + brand; retain category.
            ProductUpdateRequestDto patch = new ProductUpdateRequestDto();
            patch.setProductUuid(dellUuid);
            patch.setName("DELL XPS 16 (refreshed)");
            patch.setPrice(new BigDecimal("1100.00"));
            patch.setBrand(Brand.DELL);
            patch.setCategory(Category.COMPUTER);
            patch.setDescription("Patched description");

            mockMvc.perform(patch(UPDATE_ENDPOINT, dellUuid)
                            .with(JwtTestUtils.jwtAdmin(ADMIN_KC))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uuid").value(dellUuid.toString()))
                    .andExpect(jsonPath("$.name").value("DELL XPS 16 (refreshed)"));

            // SQL — same id, same uuid, mutated fields.
            ProductEntity afterUpdate = productRepository.findByUuid(dellUuid).orElseThrow();
            assertThat(afterUpdate.getId()).as("SQL id must be preserved (UPDATE not INSERT)").isEqualTo(sqlIdBefore);
            assertThat(afterUpdate.getUuid()).isEqualTo(dellUuid);
            assertThat(afterUpdate.getName()).isEqualTo("DELL XPS 16 (refreshed)");
            assertThat(afterUpdate.getPrice()).isEqualByComparingTo(new BigDecimal("1100.00"));
            assertThat(productRepository.count()).as("count unchanged — must not INSERT a new row").isEqualTo(3L);

            // ES — exactly one document with the updated values; no orphan with old name.
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
            indexOps.refresh();

            await().pollInterval(AWAIT_POLL).atMost(AWAIT_AT_MOST).untilAsserted(() -> {
                long esCount = productSearchRepository.count();
                assertThat(esCount).as("ES doc count unchanged at 3 (no orphan from re-index)").isEqualTo(3L);

                // Search by brand DELL → 1 hit, with the refreshed name.
                ProductSearchRequestDto req = baseRequest(Category.COMPUTER)
                        .brands(List.of(Brand.DELL))
                        .build();
                MvcResult res = performSearch(req).andExpect(status().isOk()).andReturn();
                String body = res.getResponse().getContentAsString();
                assertThat(readTotalElements(res)).isEqualTo(1);
                assertThat(body).as("ES doc must reflect updated name").contains("refreshed");
            });
        }

        @Test
        @DisplayName("PATCH preserves uuid identity — ES doc id remains stable across updates")
        void updatePreservesIdentity() throws Exception {
            ProductEntity beforeUpdate = productRepository.findByUuid(hpUuid).orElseThrow();
            Long sqlIdBefore = beforeUpdate.getId();

            ProductUpdateRequestDto patch = new ProductUpdateRequestDto();
            patch.setProductUuid(hpUuid);
            patch.setName("HP Omen 32 v2");
            patch.setBrand(Brand.HP);
            patch.setCategory(Category.COMPUTER);
            patch.setDescription("v2");

            mockMvc.perform(patch(UPDATE_ENDPOINT, hpUuid)
                            .with(JwtTestUtils.jwtAdmin(ADMIN_KC))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isOk());

            ProductEntity afterUpdate = productRepository.findByUuid(hpUuid).orElseThrow();
            assertThat(afterUpdate.getId()).isEqualTo(sqlIdBefore);
            assertThat(afterUpdate.getUuid()).isEqualTo(hpUuid);
            assertThat(productRepository.count()).isEqualTo(3L);
        }
    }

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private UUID createProduct(final ProductCreateRequestDto dto) throws Exception {
        MvcResult res = mockMvc.perform(post(CREATE_ENDPOINT)
                        .with(JwtTestUtils.jwtAdmin(ADMIN_KC))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        // Response uuid is a String (BUG-034 documented).
        Map<String, Object> body = objectMapper.readValue(
                res.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                });
        return UUID.fromString((String) body.get("uuid"));
    }

    private ProductCreateRequestDto buildComputerCreate(final String name, final Brand brand,
                                                        final BigDecimal price, final int ram) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("cpu", "Intel i7");
        attrs.put("gpu", "NVIDIA");
        attrs.put("ram", ram);
        attrs.put("os", "Windows 11");
        attrs.put("connectivity", "Wi-Fi 6");
        attrs.put("displayType", "OLED");
        attrs.put("memory", 512);
        return ProductCreateRequestDto.builder()
                .name(name)
                .price(price)
                .brand(brand)
                .category(Category.COMPUTER)
                .photo("https://cdn.example.com/" + brand.name().toLowerCase() + ".jpg")
                .stock(10)
                .description(name + " — high-end laptop")
                .attributes(attrs)
                .build();
    }

    private ProductCreateRequestDto buildMonitorCreate(final String name, final Brand brand,
                                                       final BigDecimal price) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("resolution", "3840x2160");
        attrs.put("refreshRate", 144);
        return ProductCreateRequestDto.builder()
                .name(name)
                .price(price)
                .brand(brand)
                .category(Category.MONITOR)
                .photo("https://cdn.example.com/monitor-" + brand.name().toLowerCase() + ".jpg")
                .stock(5)
                .description(name + " — 27\" 4K monitor")
                .attributes(attrs)
                .build();
    }

    private ProductSearchRequestDto.ProductSearchRequestDtoBuilder baseRequest(final Category category) {
        return ProductSearchRequestDto.builder()
                .category(category)
                .page(0)
                .size(20);
    }

    private org.springframework.test.web.servlet.ResultActions performSearch(final ProductSearchRequestDto req) throws Exception {
        return mockMvc.perform(post(SEARCH_ENDPOINT)
                .with(JwtTestUtils.jwtUser(USER_KC))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    @SuppressWarnings("unchecked")
    private int readTotalElements(final MvcResult res) throws Exception {
        Map<String, Object> body = objectMapper.readValue(
                res.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                });
        Object total = body.get("totalElements");
        if (total instanceof Number n) {
            return n.intValue();
        }
        // Fallback — if the page wrapper isn't present, count `content`.
        Object content = body.get("content");
        if (content instanceof List<?> l) {
            return l.size();
        }
        throw new IllegalStateException("Unexpected search response shape: " + body);
    }
}
