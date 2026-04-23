package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.implementation.SmsNotificationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link SmsNotificationProcessor}.
 *
 * Pins {@code BUG-2510}: {@code sendMessage} is a logging stub — no SMS gateway integration.
 * Users who pick {@code CommunicationChanel.SMS} silently receive nothing.
 */
@ExtendWith(MockitoExtension.class)
class SmsNotificationProcessorTest {

    private final SmsNotificationProcessor processor = new SmsNotificationProcessor();

    @Test
    @DisplayName("BUG-2510: sendMessage is a stub — succeeds on null context with zero side-effects (pin)")
    void sendMessageIsStub() {
        NotificationContext ctx = NotificationContext.builder()
                .user(UserContactDto.builder().phoneNumber("+33600000000").build())
                .subject("any")
                .build();

        // The stub should never throw — it only writes a log line.
        assertThatCode(() -> processor.sendMessage(ctx)).doesNotThrowAnyException();

        // BUG-2510 reproducer: even null is silently swallowed because the body never reads anything from the context.
        assertThatCode(() -> processor.sendMessage(null)).doesNotThrowAnyException();

        // BUG-2510: there is no SMS-gateway field on the class — proof that this is a stub, not a real integration.
        assertThat(java.util.Arrays.stream(processor.getClass().getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .toList())
                .as("SmsNotificationProcessor has zero non-static collaborators — no Twilio/Vonage/SNS client")
                .isEmpty();
    }

    @Test
    @DisplayName("annotated as the SMS handler so DI wires it for CommunicationChanel.SMS users")
    void classCarriesCommunicationTypeHandlerAnnotationForSms() {
        com.novatech.cybertech.annotation.CommunicationTypeHandler annotation =
                processor.getClass().getAnnotation(com.novatech.cybertech.annotation.CommunicationTypeHandler.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(CommunicationChanel.SMS);

        // Sanity: there is no field whose name suggests an SMS gateway.
        for (Field f : processor.getClass().getDeclaredFields()) {
            assertThat(f.getName().toLowerCase()).doesNotContain("sms", "gateway", "twilio", "vonage", "sns");
        }
    }
}
