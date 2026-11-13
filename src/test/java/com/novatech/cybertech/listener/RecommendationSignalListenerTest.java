package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.events.RecommendationSignalsEvent;
import com.novatech.cybertech.services.core.UserEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationSignalListenerTest {

    @Mock
    private UserEventService userEventService;

    @InjectMocks
    private RecommendationSignalListener listener;

    @Test
    @DisplayName("hands the signals of the committed action to UserEventService")
    void forwardsSignals() {
        final List<RecommendationSignalDto> signals = List.of(RecommendationSignalDto.builder()
                .userId("kc-1").eventType(UserEventType.ADD_TO_CART).productUuid(UUID.randomUUID()).build());

        listener.on(new RecommendationSignalsEvent(this, signals));

        verify(userEventService).recordServerSignals(signals);
    }

    @Test
    @DisplayName("runs async, after commit only: a rolled-back action never reaches MongoDB")
    void asyncAfterCommit() throws NoSuchMethodException {
        final var method = RecommendationSignalListener.class.getMethod("on", RecommendationSignalsEvent.class);

        assertThat(method.getAnnotation(Async.class)).isNotNull();
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
