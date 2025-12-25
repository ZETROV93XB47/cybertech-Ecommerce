package com.novatech.cybertech.batch.base;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.LocalDateTime;

@Slf4j
public abstract class BaseTasklet implements Tasklet, StepExecutionListener {

    private static final ScopedValue<StepArguments> STEP_SETTINGS = ScopedValue.newInstance();

    public record StepArguments(
            String jobName,
            LocalDateTime date,
            JobParameters jobParameters,
            StepExecution stepExecution
    ) {
    }

    @Override
    public void beforeStep(final StepExecution stepExecution) {
        log.info("Start running step : {}", stepExecution.getStepName());
    }

    public abstract RepeatStatus execute(final StepContribution stepContribution, final StepArguments stepArguments) throws Exception;

    @Override
    public RepeatStatus execute(final StepContribution stepContribution, final ChunkContext context) {
        final JobExecution jobExecution = context.getStepContext().getStepExecution().getJobExecution();

        final StepArguments taskletProps = new StepArguments(
                jobExecution.getJobInstance().getJobName().trim(),
                jobExecution.getJobParameters().getLocalDateTime("DATE", LocalDateTime.now()),
                jobExecution.getJobParameters(),
                context.getStepContext().getStepExecution());

        return ScopedValue.where(STEP_SETTINGS, taskletProps).call(() -> {

            try {
                return execute(stepContribution, taskletProps);
            }
            catch (Exception e) {
                log.error("An Error occurred during step : {} : Exception : {} \n", stepContribution.getStepExecution().getStepName(), e);
                return RepeatStatus.FINISHED;
            }
        });
    }

    @Override
    public ExitStatus afterStep(final StepExecution stepExecution) {
        log.info("Complete running step {} with exit status : {}", stepExecution.getStepName(), stepExecution.getExitStatus());
        return stepExecution.getExitStatus();
    }

}
