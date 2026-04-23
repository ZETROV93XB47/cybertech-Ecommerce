/**
 * AssertJ-style helpers shared across the controller-slice and integration tests.
 *
 * <p>Helpers in this package are pure assertion utilities: they accept a {@link
 * org.springframework.mock.web.MockHttpServletResponse} (or other inputs) and throw
 * {@link AssertionError} on failure. They exist to remove repetitive STRICT-JSON
 * boilerplate around the project's {@code ErrorResponseDto} envelope.
 */
package com.novatech.cybertech.fixtures.assertions;
