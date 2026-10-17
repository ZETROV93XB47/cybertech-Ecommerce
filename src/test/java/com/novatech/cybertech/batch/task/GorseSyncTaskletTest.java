package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.gorse.GorseMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.UserEventRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GorseSyncTasklet}. Mirrors the mocking style of
 * {@code CleanUpExpiredStockReservationsTaskletTest} — mappers are {@code @Mock}ed here (real
 * mapping logic is covered separately by {@code GorseMapperTest}).
 */
@ExtendWith(MockitoExtension.class)
class GorseSyncTaskletTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserEventRepository userEventRepository;

    @Mock
    private GorseClient gorseClient;

    @Mock
    private GorseMapper gorseMapper;

    @InjectMocks
    private GorseSyncTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        final JobInstance jobInstance = new JobInstance(1L, "GORSE_SYNC");
        final JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        final StepExecution stepExecution = new StepExecution("gorseSyncStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments(
                "GORSE_SYNC",
                LocalDateTime.now(),
                new JobParameters(),
                stepExecution
        );

        lenient().when(productRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(userRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(userEventRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
    }

    private UserEvent eventWithProduct(final String productId) {
        return UserEvent.builder()
                .userId("keycloak-user")
                .sessionId("session-1")
                .eventType(UserEventType.VIEW)
                .productId(productId)
                .timestamp(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Empty repositories")
    class EmptyRepositories {

        @Test
        @DisplayName("no products/users/events → GorseClient is never called, COMPLETED reported")
        void allEmpty_noClientCalls() throws Exception {
            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            verify(gorseClient, never()).upsertItems(anyList());
            verify(gorseClient, never()).upsertUsers(anyList());
            verify(gorseClient, never()).upsertFeedback(anyList());
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("products found → mapped and pushed via upsertItems")
        void syncsItems() throws Exception {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            final GorseItemDto itemDto = GorseItemDto.builder().itemId(product.getUuid().toString()).build();
            when(productRepository.findAll()).thenReturn(List.of(product));
            when(gorseMapper.toItemDto(product)).thenReturn(itemDto);

            tasklet.execute(stepContribution, stepArguments);

            verify(gorseClient).upsertItems(List.of(itemDto));
        }

        @Test
        @DisplayName("users found → mapped and pushed via upsertUsers")
        void syncsUsers() throws Exception {
            final UserEntity user = UserEntityBuilder.aValidUser();
            final GorseUserDto userDto = GorseUserDto.builder().userId(user.getKeycloakId()).build();
            when(userRepository.findAll()).thenReturn(List.of(user));
            when(gorseMapper.toUserDto(user)).thenReturn(userDto);

            tasklet.execute(stepContribution, stepArguments);

            verify(gorseClient).upsertUsers(List.of(userDto));
        }

        @Test
        @DisplayName("events with a productId → mapped and pushed via upsertFeedback")
        void syncsFeedback() throws Exception {
            final UserEvent event = eventWithProduct("product-1");
            final GorseFeedbackDto feedbackDto = GorseFeedbackDto.builder().itemId("product-1").build();
            when(userEventRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(event));
            when(gorseMapper.toFeedbackDto(event)).thenReturn(feedbackDto);

            tasklet.execute(stepContribution, stepArguments);

            verify(gorseClient).upsertFeedback(List.of(feedbackDto));
        }

        @Test
        @DisplayName("events without a productId are filtered out before mapping")
        void filtersEventsWithoutProductId() throws Exception {
            final UserEvent eventWithoutProduct = eventWithProduct(null);
            final UserEvent eventWithBlankProduct = eventWithProduct("  ");
            when(userEventRepository.findByTimestampBetween(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(eventWithoutProduct, eventWithBlankProduct));

            tasklet.execute(stepContribution, stepArguments);

            verify(gorseMapper, never()).toFeedbackDto(any(UserEvent.class));
            verify(gorseClient, never()).upsertFeedback(anyList());
        }
    }

    @Nested
    @DisplayName("Phase isolation — a failure in one phase does not block the others")
    class PhaseIsolation {

        @Test
        @DisplayName("item sync failure still lets users and feedback sync run, and COMPLETED is still reported")
        void itemFailureDoesNotBlockUsersOrFeedback() throws Exception {
            when(productRepository.findAll()).thenThrow(new RuntimeException("Gorse items call failed"));
            final UserEntity user = UserEntityBuilder.aValidUser();
            when(userRepository.findAll()).thenReturn(List.of(user));

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            verify(gorseClient, never()).upsertItems(anyList());
            verify(gorseClient).upsertUsers(anyList());
            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }

        @Test
        @DisplayName("GorseClient throwing for one phase does not stop the tasklet")
        void clientFailureDoesNotPropagate() throws Exception {
            final ProductEntity product = ProductEntityBuilder.aValidProduct();
            when(productRepository.findAll()).thenReturn(List.of(product));
            when(gorseMapper.toItemDto(product)).thenReturn(GorseItemDto.builder().build());
            doThrow(new RuntimeException("Gorse down")).when(gorseClient).upsertItems(anyList());

            final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

            assertThat(status).isEqualTo(RepeatStatus.FINISHED);
            assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        }
    }
}
