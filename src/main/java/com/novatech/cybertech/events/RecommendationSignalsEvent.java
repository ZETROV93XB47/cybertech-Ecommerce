package com.novatech.cybertech.events;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Published inside a business transaction (purchase confirmed, cart change, wishlist add, review)
 * with the recommendation signals it produced. Consumed AFTER_COMMIT by
 * {@link com.novatech.cybertech.listener.RecommendationSignalListener}, which writes them to
 * MongoDB: a rolled-back action produces nothing, and a MongoDB outage never reaches the action.
 */
@Getter
public class RecommendationSignalsEvent extends ApplicationEvent {

    private final List<RecommendationSignalDto> signals;

    public RecommendationSignalsEvent(final Object source, final List<RecommendationSignalDto> signals) {
        super(source);
        this.signals = List.copyOf(signals);
    }
}
