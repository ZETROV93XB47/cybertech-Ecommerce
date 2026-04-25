package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;

/**
 * Sole abstraction over the underlying e-mail transport (currently
 * {@link org.springframework.mail.javamail.JavaMailSender}). All notification
 * channels that fan out through e-mail go through this service.
 *
 * <p><b>Throws contract (Phase 1 hardening):</b> every delivery failure —
 * whether it originates from template / MIME setup ({@link jakarta.mail.MessagingException})
 * or from the SMTP transport itself ({@link org.springframework.mail.MailException})
 * — surfaces as a {@link NotificationDeliveryException}. <em>Implementations
 * MUST NOT swallow these failures silently</em>: the upstream listener / retry
 * policy needs to observe the failure to decide whether to retry, persist a
 * FAILED audit row, or schedule a redrive. See
 * {@link com.novatech.cybertech.services.implementation.MailServiceImp} for the
 * canonical implementation.
 */
public interface MailService {

    /**
     * Render the template referenced by the {@link EmailDto} and dispatch the
     * resulting MIME message through the configured transport.
     *
     * @param emailDto the rendered e-mail payload (recipient, subject,
     *                 template path, model attributes)
     * @throws NotificationDeliveryException if either MIME setup or the
     *                                       underlying transport fails;
     *                                       eligible for upstream retry
     */
    void sendEmail(final EmailDto emailDto);
}
