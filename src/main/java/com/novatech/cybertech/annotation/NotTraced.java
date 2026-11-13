package com.novatech.cybertech.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a bean (or a single method) out of the request trace written by
 * {@link com.novatech.cybertech.logger.RequestTraceAspect}.
 *
 * <p>Reserved for low-level plumbing that is called several times per request and whose
 * entry/exit lines would only drown the business path (cache helpers, encryption, key
 * generation...). Such classes keep their own targeted {@code log.*} statements.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface NotTraced {
}
