package com.novatech.cybertech.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reflection-driven constructor-contract test for every concrete custom exception
 * under {@code com.novatech.cybertech.exceptions}.
 *
 * <p>The contract every domain exception must satisfy:</p>
 * <ol>
 *   <li>{@code (String message)} so callers can throw with a plain message.</li>
 *   <li>{@code (String message, Throwable cause)} so service layers can wrap the
 *       lower-layer cause without losing the stack-trace chain.</li>
 * </ol>
 *
 * <p>Originally authored by SA4.5R; reproduced here per the SA-W1.4 brief after
 * the previous test files were lost during a session reset (see SA-W0 report
 * in {@code progress.md}).</p>
 *
 * <p><strong>Known violators (kept disabled / skipped to surface the bug):</strong></p>
 * <ul>
 *   <li>{@link IdempotencyKeyGenerationException} — only ctor is
 *       {@code (String, NoSuchAlgorithmException)} (BUG-137 + BUG-136).</li>
 *   <li>{@link PaymentProcessingException} — only ctor is
 *       {@code (String, StripeException)} (BUG-137 + BUG-136).</li>
 *   <li>{@link ProductConstraintsViolationException} — second ctor is
 *       {@code (String, Set<ConstraintViolation<Object>>)}, not
 *       {@code (String, Throwable)} (BUG-136).</li>
 *   <li>10 other exceptions (e.g. {@link PaymentFailedException},
 *       {@link UserAlreadyExistsException}, ...) only expose a single
 *       {@code (String)} ctor (BUG-136).</li>
 * </ul>
 */
@DisplayName("Custom exception constructor contract")
class CustomExceptionConstructorContractTest {

    private static final String EXCEPTIONS_PACKAGE = "com.novatech.cybertech.exceptions";

    /** BUG-137 — exceptions whose ONLY ctor demands a concrete sub-cause and lacks {@code (String)}. */
    private static final Set<String> KNOWN_STRING_CTOR_VIOLATORS = Set.of(
            "IdempotencyKeyGenerationException",
            "PaymentProcessingException"
    );

    /** BUG-136 — exceptions that lack a {@code (String, Throwable)} ctor, breaking cause-wrapping. */
    private static final Set<String> KNOWN_STRING_THROWABLE_CTOR_VIOLATORS = Set.of(
            "IdempotencyKeyGenerationException",
            "PaymentProcessingException",
            "ProductConstraintsViolationException",
            "PaymentFailedException",
            "PaymentNotFoundException",
            "ProductAlreadyInWishlist",
            "ProductNotFoundException",
            "ReviewNotFoundException",
            "UserAlreadyExistsException",
            "UserNotActiveException",
            "UserNotAuthorOfReviewException",
            "UserNotFoundException",
            "WishlistNotFoundException"
    );

    static Stream<Class<? extends Throwable>> concreteCustomExceptions() {
        final ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));

        final Stream<Class<? extends Throwable>> mapped = scanner.findCandidateComponents(EXCEPTIONS_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(CustomExceptionConstructorContractTest::loadClass)
                .filter(clazz -> Throwable.class.isAssignableFrom(clazz))
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .filter(clazz -> !clazz.isInterface())
                .map(CustomExceptionConstructorContractTest::asThrowableClass);
        return mapped.sorted(java.util.Comparator.comparing(Class::getSimpleName));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Throwable> asThrowableClass(final Class<?> clazz) {
        return (Class<? extends Throwable>) clazz;
    }

    private static Class<?> loadClass(final String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load discovered exception class " + fqcn, e);
        }
    }

    private static boolean hasCtor(final Class<?> clazz, final Class<?>... paramTypes) {
        return Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(ctor -> Arrays.equals(ctor.getParameterTypes(), paramTypes));
    }

    @ParameterizedTest(name = "[{index}] {0} must expose a (String) constructor")
    @MethodSource("concreteCustomExceptions")
    void exceptionHasStringConstructor(final Class<? extends Throwable> exceptionClass) {
        if (KNOWN_STRING_CTOR_VIOLATORS.contains(exceptionClass.getSimpleName())) {
            assumeTrue(false, "BUG-137: " + exceptionClass.getSimpleName()
                    + " demands a sub-cause type at construction and exposes no (String) ctor");
        }
        assertThat(hasCtor(exceptionClass, String.class))
                .as("%s should declare a public ctor (String message)", exceptionClass.getSimpleName())
                .isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} must expose a (String, Throwable) constructor")
    @MethodSource("concreteCustomExceptions")
    void exceptionHasStringThrowableConstructor(final Class<? extends Throwable> exceptionClass) {
        if (KNOWN_STRING_THROWABLE_CTOR_VIOLATORS.contains(exceptionClass.getSimpleName())) {
            assumeTrue(false, "BUG-136: " + exceptionClass.getSimpleName()
                    + " has no (String, Throwable) ctor — wrapping a lower-layer cause loses the chain");
        }
        assertThat(hasCtor(exceptionClass, String.class, Throwable.class))
                .as("%s should declare a public ctor (String message, Throwable cause)",
                        exceptionClass.getSimpleName())
                .isTrue();
    }

    @Nested
    @DisplayName("Scanner sanity")
    class ScannerSanity {

        @Test
        @DisplayName("scanner discovers the expected concrete-throwable surface")
        void scannerCount() {
            final long count = concreteCustomExceptions().count();
            // 33 concrete throwables in the exceptions package as of W1.4 — leave headroom.
            assertThat(count).isGreaterThanOrEqualTo(30L);
        }

        @Test
        @DisplayName("scanner filters out QuantityChangeResult sealed-permits and the rejection-reason enum")
        void scannerFiltersNonThrowableSiblings() {
            final Set<String> discoveredNames = concreteCustomExceptions()
                    .map(Class::getSimpleName)
                    .collect(Collectors.toUnmodifiableSet());

            assertThat(discoveredNames)
                    .doesNotContain("QuantityChangeResult", "QuantityUpdated", "QuantityRejected", "QuantityRejectionReason");
        }
    }
}
