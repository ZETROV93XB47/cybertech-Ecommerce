package com.novatech.cybertech.exceptions;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One or more product lines exceed the available stock. Mapped to 409 {@code NOT_ENOUGH_STOCK};
 * {@link #getShortages()} is exposed to the client as {@code details.items} so it can point at the
 * exact lines, the message only feeds the logs.
 */
public class NotEnoughStockException extends RuntimeException {

    private final List<StockShortage> shortages;

    public NotEnoughStockException(final List<StockShortage> shortages) {
        super(buildMessage(shortages));
        this.shortages = List.copyOf(shortages);
    }

    public NotEnoughStockException(final StockShortage shortage) {
        this(List.of(shortage));
    }

    public List<StockShortage> getShortages() {
        return shortages;
    }

    private static String buildMessage(final List<StockShortage> shortages) {
        return shortages.stream()
                .map(s -> "product " + s.productUuid() + ": requested=" + s.requested() + ", available=" + s.available())
                .collect(Collectors.joining("; ", "Not enough stock for ", ""));
    }
}
