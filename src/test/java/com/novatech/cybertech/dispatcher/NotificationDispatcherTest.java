package com.novatech.cybertech.dispatcher;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.exceptions.NoStrategyFoundForProcessingTheRequest;
import com.novatech.cybertech.factory.NotificationProcessorStrategyFactory;
import com.novatech.cybertech.factory.NotificationStrategyFactory;
import com.novatech.cybertech.services.core.AbstractNotification;
import com.novatech.cybertech.services.core.NotificationProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NotificationDispatcher}. Covers the strategy×processor lookup matrix plus the
 * Bridge pass-through (the resolved processor is forwarded into {@code AbstractNotification.sendNotification}).
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationStrategyFactory notificationStrategyFactory;
    @Mock
    private NotificationProcessorStrategyFactory notificationProcessorStrategyFactory;
    @Mock
    private AbstractNotification notification;
    @Mock
    private NotificationProcessor processor;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private NotificationContext contextWith(CommunicationChanel chanel) {
        UserContactDto user = UserContactDto.builder()
                .name("Jane")
                .email("jane@example.com")
                .phoneNumber("+33600000000")
                .defaultCommunicationChanel(chanel)
                .build();
        return NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(user)
                .build();
    }

    @ParameterizedTest
    @EnumSource(CommunicationChanel.class)
    void dispatchHappyPathPerCommunicationChanelShouldResolveAndDelegate(CommunicationChanel chanel) {
        NotificationContext context = contextWith(chanel);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(notification);
        when(notificationProcessorStrategyFactory.getStrategy(chanel)).thenReturn(processor);

        dispatcher.dispatch(context);

        verify(notification).sendNotification(eq(context), eq(processor));
    }

    @Test
    void dispatchShouldForwardSameContextAndProcessorIntoAbstractNotificationBridge() {
        NotificationContext context = contextWith(CommunicationChanel.EMAIL);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(notification);
        when(notificationProcessorStrategyFactory.getStrategy(CommunicationChanel.EMAIL)).thenReturn(processor);

        dispatcher.dispatch(context);

        ArgumentCaptor<NotificationContext> ctx = ArgumentCaptor.forClass(NotificationContext.class);
        ArgumentCaptor<NotificationProcessor> proc = ArgumentCaptor.forClass(NotificationProcessor.class);
        verify(notification).sendNotification(ctx.capture(), proc.capture());
        assertThat(ctx.getValue()).isSameAs(context);
        assertThat(proc.getValue()).isSameAs(processor);
    }

    @Test
    void dispatchShouldThrowWhenNotificationStrategyMissing() {
        NotificationContext context = contextWith(CommunicationChanel.EMAIL);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(null);
        when(notificationProcessorStrategyFactory.getStrategy(CommunicationChanel.EMAIL)).thenReturn(processor);

        assertThatThrownBy(() -> dispatcher.dispatch(context))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class)
                .hasMessageContaining("ORDER_CONFIRMATION")
                .hasMessageContaining("EMAIL");
        verifyNoInteractions(notification);
    }

    @Test
    void dispatchShouldThrowWhenProcessorStrategyMissing() {
        NotificationContext context = contextWith(CommunicationChanel.SMS);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(notification);
        when(notificationProcessorStrategyFactory.getStrategy(CommunicationChanel.SMS)).thenReturn(null);

        assertThatThrownBy(() -> dispatcher.dispatch(context))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class)
                .hasMessageContaining("SMS");
        verifyNoInteractions(notification);
    }

    @Test
    void dispatchShouldThrowWhenBothStrategiesAreNull() {
        NotificationContext context = contextWith(CommunicationChanel.PUSH_NOTIFICATION);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(null);
        when(notificationProcessorStrategyFactory.getStrategy(CommunicationChanel.PUSH_NOTIFICATION)).thenReturn(null);

        assertThatThrownBy(() -> dispatcher.dispatch(context))
                .isInstanceOf(NoStrategyFoundForProcessingTheRequest.class)
                .hasMessageContaining("ORDER_CONFIRMATION")
                .hasMessageContaining("PUSH_NOTIFICATION");
        verifyNoInteractions(notification);
    }

    @Test
    void dispatchShouldNeverInvokeProcessorDirectly_processorOnlyReachesNotificationViaBridge() {
        // Pin: the dispatcher itself never calls processor.sendMessage(...); only the notification does
        NotificationContext context = contextWith(CommunicationChanel.EMAIL);
        when(notificationStrategyFactory.getStrategy(NotificationType.ORDER_CONFIRMATION)).thenReturn(notification);
        when(notificationProcessorStrategyFactory.getStrategy(CommunicationChanel.EMAIL)).thenReturn(processor);

        dispatcher.dispatch(context);

        // Processor is wired into the notification, but is never invoked at the dispatcher layer
        verify(notification).sendNotification(any(), any());
        verifyNoInteractions(processor);
    }
}
