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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CybertechOrdersUpdateJob.
 *
 * Per progress.md (BUG-114, FIXED in F2): scheduler key is now "runDate" instead of
 * now.toString(). Verified green via ArgumentCaptor on JobParameters.
 */
@ExtendWith(MockitoExtension.class)
class CybertechOrdersUpdateJobTest {

    @Mock
    private Job job;

    @Mock
    private JobLauncher jobLauncher;

    @InjectMocks
    private CybertechOrdersUpdateJob scheduler;

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

            final JobExecution returned = scheduler.startJob();

            assertThat(returned).isSameAs(exec);
            verify(jobLauncher).run(eq(job), any(JobParameters.class));
        }

        @Test
        @DisplayName("activated false so JobLauncher is never called and method returns null")
        void disabled_skipsLaunch() {
            ReflectionTestUtils.setField(scheduler, "activated", false);

            final JobExecution returned = scheduler.startJob();

            assertThat(returned).isNull();
            verifyNoInteractions(jobLauncher);
        }
    }

    @Nested
    @DisplayName("BUG-114 fixed scheduler parameter key")
    class Bug114KeyFix {

        @Test
        @DisplayName("BUG-114 (FIXED in F2): job is launched with key runDate not a timestamp string")
        void launchesWithRunDateKey() throws Exception {
            final JobExecution exec = new JobExecution(1L, new JobInstance(1L, "JOB"), new JobParameters());
            when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(exec);

            scheduler.startJob();

            final ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
            verify(jobLauncher).run(eq(job), captor.capture());
            final JobParameters captured = captor.getValue();
            final Set<JobParameter<?>> params = captured.parameters();
            assertThat(params).hasSize(1);
            final JobParameter<?> single = params.iterator().next();
            assertThat(single.name()).isEqualTo("runDate");
            assertThat(single.value()).isInstanceOf(LocalDateTime.class);
        }

        @Test
        @DisplayName("BUG-114 (FIXED): the launched parameter key is the literal runDate")
        void keyIsNotATimestampString() throws Exception {
            final JobExecution exec = new JobExecution(1L, new JobInstance(1L, "JOB"), new JobParameters());
            when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(exec);

            scheduler.startJob();

            final ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
            verify(jobLauncher).run(eq(job), captor.capture());
            final String onlyKey = captor.getValue().parameters().iterator().next().name();
            assertThat(onlyKey).isEqualTo("runDate").doesNotContain("T").doesNotStartWith("2026");
        }
    }

    @Nested
    @DisplayName("Launcher exceptions are swallowed")
    class LauncherFailures {

        @Test
        @DisplayName("JobInstanceAlreadyCompleteException returns null and does not propagate")
        void instanceAlreadyComplete_returnsNull() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobInstanceAlreadyCompleteException("dup"));

            assertThat(scheduler.startJob()).isNull();
        }

        @Test
        @DisplayName("JobExecutionAlreadyRunningException returns null and does not propagate")
        void executionAlreadyRunning_returnsNull() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobExecutionAlreadyRunningException("running"));

            assertThat(scheduler.startJob()).isNull();
        }

        @Test
        @DisplayName("JobRestartException returns null and does not propagate")
        void restartException_returnsNull() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new JobRestartException("restart"));

            assertThat(scheduler.startJob()).isNull();
        }

        @Test
        @DisplayName("InvalidJobParametersException returns null and does not propagate")
        void invalidParams_returnsNull() throws Exception {
            when(jobLauncher.run(eq(job), any(JobParameters.class)))
                    .thenThrow(new InvalidJobParametersException("bad"));

            assertThat(scheduler.startJob()).isNull();
        }
    }

    @Nested
    @DisplayName("No real Spring Batch context is required")
    class NoBatchContext {

        @Test
        @DisplayName("scheduler is callable with all-mocked collaborators")
        void worksWithPureMocks() throws Exception {
            final JobExecution exec = new JobExecution(1L, new JobInstance(1L, "JOB"), new JobParameters());
            when(jobLauncher.run(eq(job), any(JobParameters.class))).thenReturn(exec);

            assertThat(scheduler.startJob()).isSameAs(exec);
        }

        @Test
        @DisplayName("disabled flag short-circuits before any interaction")
        void disabledShortCircuits() throws Exception {
            ReflectionTestUtils.setField(scheduler, "activated", false);

            scheduler.startJob();

            verify(jobLauncher, never()).run(any(Job.class), any(JobParameters.class));
        }
    }
}
