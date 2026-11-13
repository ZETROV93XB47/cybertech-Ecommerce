package com.novatech.cybertech.batch.job.implementation;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.CLEAN_UP_EXPIRED_STOCK_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.GORSE_SYNC_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.KEYCLOAK_OUTBOX_RECONCILIATION_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.REDELIVER_FAILED_NOTIFICATIONS_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@code lombok.copyableAnnotations += ...Qualifier} entry of {@code lombok.config}.
 *
 * <p>Every {@code *JobImp} declares {@code @Qualifier(X) private final Job job;} and relies on
 * {@code @RequiredArgsConstructor}. Without the lombok.config entry the qualifier stays on the
 * field and never reaches the constructor parameter, so Spring silently injected the
 * {@code @Primary} orders job into all five schedulers: the orders job (ship PAID orders included)
 * ran several times concurrently and the stock-cleanup / gorse / redelivery / keycloak jobs
 * never ran at all.
 */
class BatchJobQualifierWiringTest {

    @Test
    void everyJobImpConstructorCarriesItsOwnJobQualifier() {
        assertJobQualifier(CybertechOrdersUpdateJobImp.class, REPORT_FAILED_PAYMENT_AND_CANCELLED_ORDERS_JOB);
        assertJobQualifier(StockCleanupJobImp.class, CLEAN_UP_EXPIRED_STOCK_JOB);
        assertJobQualifier(GorseSyncJobImp.class, GORSE_SYNC_JOB);
        assertJobQualifier(RedeliverFailedNotificationsJobImp.class, REDELIVER_FAILED_NOTIFICATIONS_JOB);
        assertJobQualifier(KeycloakOutboxReconciliationJobImp.class, KEYCLOAK_OUTBOX_RECONCILIATION_JOB);
    }

    private static void assertJobQualifier(final Class<?> jobImp, final String expectedQualifier) {
        final List<Parameter> jobParameters = Arrays.stream(jobImp.getDeclaredConstructors())
                .map(Constructor::getParameters)
                .flatMap(Arrays::stream)
                .filter(parameter -> Job.class.equals(parameter.getType()))
                .toList();

        assertThat(jobParameters).as("%s constructor Job parameter", jobImp.getSimpleName()).hasSize(1);
        assertThat(jobParameters.getFirst().getAnnotation(Qualifier.class))
                .as("%s constructor Job parameter @Qualifier (check lombok.config)", jobImp.getSimpleName())
                .isNotNull()
                .extracting(Qualifier::value)
                .isEqualTo(expectedQualifier);
    }
}
