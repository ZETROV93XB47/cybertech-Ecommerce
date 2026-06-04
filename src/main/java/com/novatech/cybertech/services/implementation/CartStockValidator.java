package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.exceptions.NotEnoughStockException;

/**
 * Shared, stateless stock-availability guard for the cart write paths.
 *
 * <p>Extracted out of {@code CartServiceImp} when the add-items read-modify-write moved into
 * {@link CartWriteTransactionalDelegateImp}: both {@code CartServiceImp.updateCart(...)} and the
 * delegate need the exact same "would this push reserved + requested past total stock?" rule, and
 * the project forbids duplicating a utility across classes. Keeping it as a pure {@code static}
 * function (no Spring bean, no state) means it runs verbatim in plain Mockito unit tests on either
 * caller.
 *
 * <p>The check is deliberately <em>advisory</em> at cart time. It reads {@link ProductEntity#getStock()}
 * / {@link ProductEntity#getReservedStock()} — shared cross-user state — so two <em>different</em>
 * users adding the same product concurrently can both pass it. The authoritative, atomic stock
 * reservation happens later at order placement; this guard only gives the shopper fast, friendly
 * feedback before they ever reach checkout.
 */
final class CartStockValidator {

    private CartStockValidator() {
        // Utility holder — never instantiated.
    }

    /**
     * Rejects a cart write that would make {@code reservedStock + requestedQty} exceed the product's
     * total {@code stock}.
     *
     * <p>Example — product with {@code stock=10}, {@code reservedStock=8}:
     * <ul>
     *   <li>{@code requestedQty=2} → {@code 8 + 2 = 10 <= 10} → passes (boundary).</li>
     *   <li>{@code requestedQty=3} → {@code 8 + 3 = 11 > 10} → throws, message advertises
     *       {@code Available: 2} so the caller can retry with a smaller quantity.</li>
     * </ul>
     *
     * @param product      product whose stock is being checked.
     * @param requestedQty total quantity the caller wants in the cart for that product.
     * @throws NotEnoughStockException when the request would exceed available stock.
     */
    static void validateStockAvailability(final ProductEntity product, final int requestedQty) {
        if (product.getReservedStock() + requestedQty > product.getStock()) {
            throw new NotEnoughStockException("Not enough stock for product " + product.getName() + ". Available: " + (product.getStock() - product.getReservedStock()));
        }
    }
}
