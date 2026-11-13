package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.repositories.StockAlertRepository;
import com.novatech.cybertech.services.core.NotificationRetryableDelivery;
import com.novatech.cybertech.services.core.StockAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link StockAlertTasklet}: expiry first, paging, per-alert isolation. */
@ExtendWith(MockitoExtension.class)
class StockAlertTaskletTest {

    private static final int BATCH_SIZE = 2;

    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private StockAlertService stockAlertService;
    @Mock
    private NotificationRetryableDelivery retryableDelivery;

    @InjectMocks
    private StockAlertTasklet tasklet;

    private StepContribution stepContribution;
    private BaseTasklet.StepArguments stepArguments;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tasklet, "batchSize", BATCH_SIZE);
        final JobExecution jobExecution = new JobExecution(1L, new JobInstance(1L, "STOCK_ALERT_JOB"), new JobParameters());
        final StepExecution stepExecution = new StepExecution("stockAlertStep", jobExecution);
        stepContribution = new StepContribution(stepExecution);
        stepArguments = new BaseTasklet.StepArguments("STOCK_ALERT_JOB", LocalDateTime.now(), new JobParameters(), stepExecution);
    }

    private static NotificationContext<StockAlertPayload> notification(final CommunicationChanel channel, final String name) {
        return NotificationContext.<StockAlertPayload>builder()
                .communicationChanel(channel)
                .payload(StockAlertPayload.builder().productName(name).build())
                .build();
    }

    @Test
    @DisplayName("expires old alerts first, then pages by last id and delivers every notification")
    void expiresThenPagesAndDelivers() {
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(3L, 4L));
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(4L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(9L));
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(9L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());
        final NotificationContext<StockAlertPayload> email3 = notification(CommunicationChanel.EMAIL, "a");
        final NotificationContext<StockAlertPayload> push3 = notification(CommunicationChanel.PUSH_NOTIFICATION, "a");
        final NotificationContext<StockAlertPayload> email9 = notification(CommunicationChanel.EMAIL, "b");
        when(stockAlertService.prepareAlert(3L)).thenReturn(List.of(email3, push3));
        when(stockAlertService.prepareAlert(4L)).thenReturn(List.of());
        when(stockAlertService.prepareAlert(9L)).thenReturn(List.of(email9));

        final RepeatStatus status = tasklet.execute(stepContribution, stepArguments);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        final InOrder order = inOrder(stockAlertService, stockAlertRepository);
        order.verify(stockAlertService).expireOldAlerts();
        order.verify(stockAlertRepository).findPendingIdsWithAvailableProductAfter(0L, PageRequest.of(0, BATCH_SIZE));
        verify(retryableDelivery).deliver(email3);
        verify(retryableDelivery).deliver(push3);
        verify(retryableDelivery).deliver(email9);
        verify(retryableDelivery, times(3)).deliver(any());
    }

    @Test
    @DisplayName("a failing alert does not stop the others")
    void failingAlertDoesNotStopTheJob() {
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of(1L, 2L));
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(2L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());
        final NotificationContext<StockAlertPayload> email2 = notification(CommunicationChanel.EMAIL, "second");
        when(stockAlertService.prepareAlert(1L)).thenThrow(new IllegalStateException("db hiccup"));
        when(stockAlertService.prepareAlert(2L)).thenReturn(List.of(email2));

        tasklet.execute(stepContribution, stepArguments);

        verify(retryableDelivery).deliver(email2);
        assertThat(stepContribution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("no alert to send: only the expiry runs")
    void nothingToSend() {
        when(stockAlertRepository.findPendingIdsWithAvailableProductAfter(0L, PageRequest.of(0, BATCH_SIZE))).thenReturn(List.of());

        tasklet.execute(stepContribution, stepArguments);

        verify(stockAlertService).expireOldAlerts();
        verify(stockAlertService, never()).prepareAlert(anyLong());
        verify(retryableDelivery, never()).deliver(any());
    }
}
