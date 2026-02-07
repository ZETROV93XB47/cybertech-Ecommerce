package com.novatech.cybertech.batch.task;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.services.core.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static com.novatech.cybertech.constants.CyberTechAppConstants.FAILED_PAYMENT_ORDERS_MAP_BY_USERS;
import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_SUMMARY_REPORT_JOB;
import static com.novatech.cybertech.constants.CyberTechAppConstants.PENDING_ORDERS_MAP_BY_USER_EMAIL;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrdersSummaryReportListener implements JobExecutionListener {

    @Value("${email.sender.address}")
    private String emailSender;

    private final MailService mailService;
    private final SpringTemplateEngine templateEngine;

    @Override
    public void afterJob(final JobExecution jobExecution) {

        log.info("Starting OrdersSummaryReportListener afterJob");

        if (!jobExecution.getStatus().isUnsuccessful()) {
            sendPendingOrdersEmails(jobExecution);
            sendCancelledOrdersEmails(jobExecution);
        }
        else {
            log.info("{} Job finished with errors", ORDER_SUMMARY_REPORT_JOB);
        }

        log.info("Finished OrdersSummaryReportListener afterJob");
    }

    private void sendCancelledOrdersEmails(final JobExecution jobExecution) {
        final Map<String, List<UUID>> cancelledOrdersMapByUserEmail = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(PENDING_ORDERS_MAP_BY_USER_EMAIL);

        if (cancelledOrdersMapByUserEmail == null) {
            log.info("No cancelled orders map found, No cancelled orders emails will be sent");
            return;
        }

        createEmailDtoAndSendEmail(cancelledOrdersMapByUserEmail, EmailTemplateType.ORDER_CANCELLATION);
    }

    private void sendPendingOrdersEmails(final JobExecution jobExecution) {
        final Map<String, List<UUID>> pendingPaymentOrdersMapByUserEmail = (Map<String, List<UUID>>) jobExecution.getExecutionContext().get(FAILED_PAYMENT_ORDERS_MAP_BY_USERS);

        if (pendingPaymentOrdersMapByUserEmail == null) {
            log.info("No pending  orders map found, No pending orders emails will be sent");
            return;
        }

        createEmailDtoAndSendEmail(pendingPaymentOrdersMapByUserEmail, EmailTemplateType.ORDER_PENDING_PAYMENT);
    }

    private void createEmailDtoAndSendEmail(final Map<String, List<UUID>> ordersMapByUserEmail, final EmailTemplateType emailTemplateType) {
        final List<EmailDto> emailDtoList = ordersMapByUserEmail.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> context = new HashMap<>();
                    context.put("orderId", entry.getValue());
                    return EmailDto.builder()
                            .from(emailSender)
                            .to(entry.getKey())
                            .subject(emailTemplateType.getSubject())
                            .context(context)
                            .templatePath(emailTemplateType.getTemplatePath())
                            .build();
                })
                .toList();

        emailDtoList.forEach(mailService::sendEmail);
    }

}
