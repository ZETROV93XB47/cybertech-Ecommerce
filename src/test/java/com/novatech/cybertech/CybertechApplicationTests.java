package com.novatech.cybertech;

import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CybertechApplicationTests {


    @Autowired
    ProductSearchRepository repository;

    @Test
    void contextLoads() {

        ProductDocument doc = ProductDocument.builder()
                .id("p1")
                .name("Gaming Laptop")
                .description("Fast machine with RTX GPU")
                .brand("BrandX")
                .price(BigDecimal.valueOf(1500))
                .build();

        repository.save(doc);

        List<ProductDocument> results = repository.fullTextSearch("laptop");
        assertFalse(results.isEmpty());


    }

}
