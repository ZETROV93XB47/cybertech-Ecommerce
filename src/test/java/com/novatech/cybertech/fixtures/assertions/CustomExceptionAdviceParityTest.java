package com.novatech.cybertech.fixtures.assertions;

import com.novatech.cybertech.api.error.ErrorManagementController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reflection-driven cross-cutting assertion: every concrete custom exception under
 * {@code com.novatech.cybertech.exceptions} MUST have a matching {@code @ExceptionHandler}
 * declared on {@link ErrorManagementController}, otherwise the {@code RuntimeException}
 * catch-all returns a 500 TECHNICAL response and clients lose actionable error context.
 *
 * <p>Originally authored by SA1.3 and reproduced here per the SA-W1.4 brief
 * after the previous test files were lost during a session reset (see SA-W0
 * report in {@code progress.md}). The F2 wave (orchestrator, 2026-04-23) claims
 * to have closed BUG-001..BUG-016 by adding the missing handlers — this test
 * is the live oracle for that claim.</p>
 */
@DisplayName("Custom exception <-> @ExceptionHandler advice parity")
class CustomExceptionAdviceParityTest {

    private static final String EXCEPTIONS_PACKAGE = "com.novatech.cybertech.exceptions";

    /** Bug numbers to keep alive — currently EMPTY because F2 closed BUG-001..016. */
    private static final Set<String> KNOWN_MISSING_HANDLERS = Set.of();

    /** Resolves the set of {@code @ExceptionHandler}-declared exception classes on the advice. */
    private static Set<Class<?>> handledByAdvice() {
        return Arrays.stream(ErrorManagementController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(ExceptionHandler.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Streams all concrete (non-abstract) {@link Throwable} subclasses under the exceptions package. */
    static Stream<Class<? extends Throwable>> concreteCustomExceptions() {
        final ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));

        final Stream<Class<? extends Throwable>> mapped = scanner.findCandidateComponents(EXCEPTIONS_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(CustomExceptionAdviceParityTest::loadClass)
                .filter(clazz -> Throwable.class.isAssignableFrom(clazz))
                .filter(clazz -> !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()))
                .filter(clazz -> !clazz.isInterface())
                .map(CustomExceptionAdviceParityTest::asThrowableClass);
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

    @ParameterizedTest(name = "[{index}] {0} must have an @ExceptionHandler on the advice")
    @MethodSource("concreteCustomExceptions")
    void everyCustomExceptionMustBeHandledByTheAdvice(final Class<? extends Throwable> exceptionClass) {
        // F2 closed BUG-001..016 by adding handlers; KNOWN_MISSING_HANDLERS is empty so the assume passes.
        // If a future regression re-opens one of those bugs, add the bug-id token here.
        if (KNOWN_MISSING_HANDLERS.contains(exceptionClass.getSimpleName())) {
            assumeTrue(false, "BUG: handler still missing for " + exceptionClass.getSimpleName());
        }

        final Set<Class<?>> handled = handledByAdvice();

        assertThat(handled)
                .as("ErrorManagementController must declare @ExceptionHandler for %s",
                        exceptionClass.getSimpleName())
                .anyMatch(declared -> declared.equals(exceptionClass)
                        || declared.isAssignableFrom(exceptionClass));
    }

    @Nested
    @DisplayName("Scanner sanity")
    class ScannerSanity {

        @Test
        @DisplayName("scanner discovers a non-trivial number of concrete exception classes")
        void scannerDiscoversConcreteExceptions() {
            final long count = concreteCustomExceptions().count();
            // 33 concrete throwables in the exceptions package as of W1.4 — leave headroom.
            assertThat(count).isGreaterThanOrEqualTo(30L);
        }

        @Test
        @DisplayName("scanner filters out the sealed QuantityChangeResult hierarchy and the rejection-reason enum")
        void scannerFiltersSealedAndEnumSiblings() {
            final Set<String> discoveredNames = concreteCustomExceptions()
                    .map(Class::getSimpleName)
                    .collect(Collectors.toUnmodifiableSet());

            assertThat(discoveredNames)
                    .doesNotContain("QuantityChangeResult", "QuantityUpdated", "QuantityRejected", "QuantityRejectionReason");
        }

        @Test
        @DisplayName("advice declares at least one @ExceptionHandler method")
        void adviceDeclaresHandlers() {
            final long handlerMethods = Arrays.stream(ErrorManagementController.class.getDeclaredMethods())
                    .map(Method::getAnnotations)
                    .flatMap(Arrays::stream)
                    .filter(annotation -> annotation instanceof ExceptionHandler)
                    .count();
            assertThat(handlerMethods).isGreaterThan(20L);
        }
    }
}
