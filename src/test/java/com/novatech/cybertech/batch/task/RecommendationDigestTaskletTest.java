package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.RecommendationDigestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link RecommendationDigestTasklet}: paging over recipients and per-user isolation. */
@ExtendWith(MockitoExtension.class)
class RecommendationDigestTaskletTest {

    private static final int BATCH_SIZE = 2;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RecommendationDigestService recommendationDigestService;
    @Mock
    private NotificationRetryableDelivery retryableDelivery;

    @InjectMocks
    private RecommendationDigestTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tasklet, "batchSize", BATCH_SIZE);
        final JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "RECOMMENDATION_DIGEST_JOB"), new JobParameters());
        final StepExecution stepExecution = new StepExecution("recommendationDigestStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments("RECOMMENDATION_DIGEST_JOB", LocalDateTime.now(), new JobParameters(), stepExecution);
    }

    private static NotificationContext<RecommendationDigestPayload> digest(final String unsubscribeUrl) {
        final RecommendationDigestPayload payload = RecommendationDigestPayload.builder().unsubscribeUrl(unsubscribeUrl).build();
        return NotificationContext.<RecommendationDigestPayload>builder().payload(payload).build();
    }

    @Test
    @DisplayName("pages by last id until an empty page; delivers each prepared e-mail")
    void pagesThroughAllRecipients() {
        when(userRepository.findRecommendationEmailRecipientIdsAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(1L, 2L));
        when(userRepository.findRecommendationEmailRecipientIdsAfter(2L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(5L));
        when(userRepository.findRecommendationEmailRecipientIdsAfter(5L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());
        final NotificationContext<RecommendationDigestPayload> first = digest("first");
        final NotificationContext<RecommendationDigestPayload> third = digest("third");
        when(recommendationDigestService.prepareDigest(1L)).thenReturn(Optional.of(first));
        when(recommendationDigestService.prepareDigest(2L)).thenReturn(Optional.empty());
        when(recommendationDigestService.prepareDigest(5L)).thenReturn(Optional.of(third));

        final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        verify(retryableDelivery).deliver(first);
        verify(retryableDelivery).deliver(third);
        verify(retryableDelivery, times(2)).deliver(any());
    }

    @Test
    @DisplayName("a failing user does not stop the others")
    void failingUserDoesNotStopTheJob() {
        when(userRepository.findRecommendationEmailRecipientIdsAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(1L, 2L));
        when(userRepository.findRecommendationEmailRecipientIdsAfter(2L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());
        final NotificationContext<RecommendationDigestPayload> second = digest("second");
        when(recommendationDigestService.prepareDigest(1L)).thenThrow(new IllegalStateException("db hiccup"));
        when(recommendationDigestService.prepareDigest(2L)).thenReturn(Optional.of(second));

        tasklet.execute(stepContribution, stepArguments);

        verify(retryableDelivery).deliver(second);
        assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("no recipient: nothing prepared nor sent")
    void noRecipient() {
        when(userRepository.findRecommendationEmailRecipientIdsAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());

        tasklet.execute(stepContribution, stepArguments);

        verify(recommendationDigestService, never()).prepareDigest(any());
        verify(retryableDelivery, never()).deliver(any());
    }
}
