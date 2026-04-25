package com.novatech.cybertech.batch.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedeliverFailedNotificationsJob}. Mirrors the
 * {@code StockCleanupJobTest} structure: activation flag short-circuit,
 * fixed {@code "date"} parameter key, launcher exceptions swallowed.
 */
@ExtendWith(MockitoExtension.class)
class RedeliverFailedNotificationsJobTest {

    @Mock
    private Job job;

    @Mock
    private JobLauncher jobLauncher;

    @InjectMocks
    private RedeliverFailedNotificationsJob scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "activated", true);
    }

    @Nested
    @DisplayName("Activation flag")
    class Activation {

        @Test
        @DisplayName("activated true so JobLauncher.run is invoked exactly once")
        void enabled_launchesJob() throws Exception {
            final JobExecution exec = new JobExecution(1L, new JobInstance(1L, "JOB"), new JobParameters());
            when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(exec);

            scheduler.startJob();

            verify(jobLauncher).run(eq(job), any(JobParameters.class));
        }

        @Test
        @DisplayName("activated false so JobLauncher is never called")
        void disabled_skipsLaunch() {
            ReflectionTestUtils.setField(scheduler, "activated", false);

            scheduler.startJob();

            verifyNoInteractions(jobLauncher);
        }

        @Test
        @DisplayName("disabled flag short-circuits before any Job interaction")
        void disabledShortCircuits() throws Exception {
            ReflectionTestUtils.setField(scheduler, "activated", false);

            scheduler.startJob();

            verify(jobLauncher, never()).run(any(Job.class), any(JobParameters.class));
        }
    }

    @Nested
    @DisplayName("JobParameters key")
    class ParameterKey {

        @Test
        @DisplayName("uses fixed key date for the LocalDateTime parameter")
        void launchesWithDateKey() throws Exception {
            final JobExecution exec = new JobExecution(1L, new JobInstance(1L, "JOB"), new JobParameters());
            when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(exec);

            scheduler.startJob();

            final ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
            verify(jobLauncher).run(eq(job), captor.capture());
            final Set<JobParameter<?>> params = captor.getValue().parameters();
            assertThat(params).hasSize(1);
            final JobParameter<?> single = params.iterator().next();
            assertThat(single.name()).isEqualTo("date");
            assertThat(single.value()).isInstanceOf(LocalDateTime.class);
        }
    }

    @Nested
    @DisplayName("Launcher exceptions are swallowed")
    class LauncherFailures {

        @Test
        @DisplayName("JobInstanceAlreadyCompleteException does not propagate")
        void instanceAlreadyComplete_swallowed() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobInstanceAlreadyCompleteException("dup"));

            assertThatNoException().isThrownBy(scheduler::startJob);
        }

        @Test
        @DisplayName("JobExecutionAlreadyRunningException does not propagate")
        void alreadyRunning_swallowed() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobExecutionAlreadyRunningException("running"));

            assertThatNoException().isThrownBy(scheduler::startJob);
        }

        @Test
        @DisplayName("JobRestartException does not propagate")
        void restartException_swallowed() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobRestartException("restart"));

            assertThatNoException().isThrownBy(scheduler::startJob);
        }

        @Test
        @DisplayName("InvalidJobParametersException does not propagate")
        void invalidParams_swallowed() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new InvalidJobParametersException("bad"));

            assertThatNoException().isThrownBy(scheduler::startJob);
        }
    }
}
