package com.novatech.cybertech.annotation;

import com.novatech.cybertech.entities.enums.DiscountCalculationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code DiscountStrategy} bean as the handler for one or more
 * {@link DiscountCalculationType} algorithms (PERCENTAGE / FIXED_AMOUNT / BOGO / NONE).
 * Multiple discount campaigns (identified by their free-form {@code discountKey}, e.g.
 * {@code BLACK_FRIDAY}, {@code WINTER_SALES}) can share the same strategy via this
 * calculation-type indirection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscountTypeHandler {
    DiscountCalculationType[] value();
}
