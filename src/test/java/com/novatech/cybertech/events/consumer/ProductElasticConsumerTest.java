package com.novatech.cybertech.events.consumer;

import com.novatech.cybertech.repositories.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * BUG-120 pin: {@link ProductElasticConsumer} carries a single field, no public methods, and the
 * intended {@code @KafkaListener consumeProductEvent} body is fully commented out in production.
 * This test pins the dead-listener state so that re-enabling Kafka triggers a regression here first.
 */
@ExtendWith(MockitoExtension.class)
class ProductElasticConsumerTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @InjectMocks
    private ProductElasticConsumer consumer;

    @Test
    void bug120_classExposesNoPublicListenerMethodAndDoesNotCallRepository() {
        // No reachable @KafkaListener method on the class — the body is commented out in prod.
        boolean hasKafkaListener = Arrays.stream(ProductElasticConsumer.class.getDeclaredMethods())
                .map(Method::getAnnotations)
                .flatMap(Arrays::stream)
                .anyMatch(a -> a.annotationType().equals(KafkaListener.class));

        assertThat(hasKafkaListener)
                .as("BUG-120: ProductElasticConsumer has no reachable @KafkaListener — body is commented out")
                .isFalse();

        // Mirror: the only declared methods are Lombok-synthetic constructors.
        Method[] declared = ProductElasticConsumer.class.getDeclaredMethods();
        assertThat(declared)
                .as("No business methods are declared on the consumer (only synthetic accessors at most)")
                .allSatisfy(m -> assertThat(m.isSynthetic() || m.getName().startsWith("$"))
                        .as("Method %s is not a real listener body", m.getName())
                        .isTrue());

        // And no interaction would happen end-to-end on the search repository.
        verifyNoInteractions(productSearchRepository);
    }
}
