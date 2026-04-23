package com.novatech.cybertech.mappers.document;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProductDocumentMapper} is currently an empty interface (zero declared methods). The test
 * pins that the factory still produces a non-null instance so that any future addition of methods
 * gets test scaffolding to extend.
 *
 * <p>Tech-debt (carried from SA1.5): consider deleting the empty mapper or documenting what it
 * should become.
 */
class ProductDocumentMapperTest {

    @Test
    void factoryShouldProduceNonNullInstance() {
        ProductDocumentMapper mapper = Mappers.getMapper(ProductDocumentMapper.class);

        assertThat(mapper).isNotNull();
    }
}
