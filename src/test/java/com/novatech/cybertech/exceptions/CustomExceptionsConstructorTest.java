package com.novatech.cybertech.exceptions;

import com.stripe.exception.StripeException;
import jakarta.validation.ConstraintViolation;
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
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Per-class constructor + cause-chain coverage for every concrete custom exception
 * under {@code com.novatech.cybertech.exceptions}. One consolidated test class
 * (rather than one file per exception) per the SA-W1.4 brief.
 *
 * <p>Goals:</p>
 * <ul>
 *   <li>Every exception is a {@link RuntimeException} subtype (load-bearing for
 *       {@code @Transactional} rollback semantics).</li>
 *   <li>Every {@code (String)} ctor preserves the message via {@link Throwable#getMessage()}.</li>
 *   <li>Every {@code (String, Throwable)} ctor preserves both the message and the cause
 *       via {@link Throwable#getCause()}.</li>
 *   <li>Throwing an instance is catchable by its declared type.</li>
 * </ul>
 */
@DisplayName("Per-class custom exception constructor + cause-chain coverage")
class CustomExceptionsConstructorTest {

    private static final String EXCEPTIONS_PACKAGE = "com.novatech.cybertech.exceptions";

    static Stream<Class<? extends Throwable>> allConcreteCustomExceptions() {
        final ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));

        final Stream<Class<? extends Throwable>> mapped = scanner.findCandidateComponents(EXCEPTIONS_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(CustomExceptionsConstructorTest::loadClass)
                .filter(clazz -> Throwable.class.isAssignableFrom(clazz))
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .filter(clazz -> !clazz.isInterface())
                .map(CustomExceptionsConstructorTest::asThrowableClass);
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

    /** Exceptions whose only ctor is a non-(String) shape and so cannot be exercised generically here. */
    private static final Set<String> SPECIAL_CTOR_ONLY = Set.of(
            "IdempotencyKeyGenerationException",
            "PaymentProcessingException"
    );

    @Nested
    @DisplayName("RuntimeException subtype invariant")
    class RuntimeSubtype {

        @ParameterizedTest(name = "[{index}] {0} extends RuntimeException")
        @MethodSource("com.novatech.cybertech.exceptions.CustomExceptionsConstructorTest#allConcreteCustomExceptions")
        void everyExceptionIsARuntimeException(final Class<? extends Throwable> exceptionClass) {
            assertThat(RuntimeException.class)
                    .as("%s must be a RuntimeException subtype to enable @Transactional rollback",
                            exceptionClass.getSimpleName())
                    .isAssignableFrom(exceptionClass);
        }
    }

    @Nested
    @DisplayName("(String) constructor preserves message")
    class StringCtor {

        @ParameterizedTest(name = "[{index}] {0}(String) preserves message")
        @MethodSource("com.novatech.cybertech.exceptions.CustomExceptionsConstructorTest#allConcreteCustomExceptions")
        void stringCtorPreservesMessage(final Class<? extends Throwable> exceptionClass) throws Exception {
            if (SPECIAL_CTOR_ONLY.contains(exceptionClass.getSimpleName())) {
                return; // covered explicitly in SpecialCtorClasses below
            }
            final Constructor<? extends Throwable> ctor = exceptionClass.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            final Throwable instance = ctor.newInstance("boom");

            assertThat(instance.getMessage()).isEqualTo("boom");
            assertThat(instance.getCause()).isNull();
        }
    }

    @Nested
    @DisplayName("(String, Throwable) constructor preserves message and cause")
    class StringThrowableCtor {

        @ParameterizedTest(name = "[{index}] {0}(String, Throwable) preserves cause")
        @MethodSource("com.novatech.cybertech.exceptions.CustomExceptionsConstructorTest#allConcreteCustomExceptions")
        void stringThrowableCtorPreservesCause(final Class<? extends Throwable> exceptionClass) throws Exception {
            if (!hasCtor(exceptionClass, String.class, Throwable.class)) {
                return; // ctor absence is asserted in CustomExceptionConstructorContractTest (BUG-136)
            }
            final Constructor<? extends Throwable> ctor =
                    exceptionClass.getDeclaredConstructor(String.class, Throwable.class);
            ctor.setAccessible(true);
            final Throwable cause = new IllegalStateException("upstream");
            final Throwable instance = ctor.newInstance("wrapped", cause);

            assertThat(instance.getMessage()).isEqualTo("wrapped");
            assertThat(instance.getCause()).isSameAs(cause);
        }
    }

    @Nested
    @DisplayName("throw + catch round-trip")
    class ThrowCatch {

        @ParameterizedTest(name = "[{index}] throw {0} caught as RuntimeException")
        @MethodSource("com.novatech.cybertech.exceptions.CustomExceptionsConstructorTest#allConcreteCustomExceptions")
        void thrownInstanceIsCatchableAsRuntimeException(final Class<? extends Throwable> exceptionClass) throws Exception {
            if (SPECIAL_CTOR_ONLY.contains(exceptionClass.getSimpleName())) {
                return; // covered explicitly below
            }
            final Constructor<? extends Throwable> ctor = exceptionClass.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            final Throwable instance = ctor.newInstance("trip");

            final Throwable caught = catchThrowable(() -> { throw instance; });
            assertThat(caught)
                    .isSameAs(instance)
                    .isInstanceOf(RuntimeException.class)
                    .isInstanceOf(exceptionClass);
        }
    }

    private static boolean hasCtor(final Class<?> clazz, final Class<?>... paramTypes) {
        return Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(ctor -> Arrays.equals(ctor.getParameterTypes(), paramTypes));
    }

    @Nested
    @DisplayName("Special-shape ctor classes (BUG-136 / BUG-137 surfaces)")
    class SpecialCtorClasses {

        @Test
        @DisplayName("IdempotencyKeyGenerationException — only ctor is (String, NoSuchAlgorithmException)")
        void idempotencyKeyGenerationException() {
            final NoSuchAlgorithmException cause = new NoSuchAlgorithmException("MD5 missing");
            final IdempotencyKeyGenerationException ex =
                    new IdempotencyKeyGenerationException("could not hash", cause);

            assertThat(ex.getMessage()).isEqualTo("could not hash");
            // BUG-136: the existing ctor passes message-only to super, so cause is dropped.
            assertThat(ex.getCause())
                    .as("BUG-136 — cause is currently dropped because super(message) is called instead of super(message, cause)")
                    .isNull();
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("PaymentProcessingException — only ctor is (String, StripeException)")
        void paymentProcessingException() {
            final StripeException cause = new com.stripe.exception.ApiException(
                    "stripe down", "req_xyz", "api_error", 503, null);
            final PaymentProcessingException ex =
                    new PaymentProcessingException("payment failed", cause);

            assertThat(ex.getMessage()).isEqualTo("payment failed");
            // BUG-136: the existing ctor passes message-only to super, so cause is dropped.
            assertThat(ex.getCause())
                    .as("BUG-136 — cause is currently dropped because super(message) is called instead of super(message, cause)")
                    .isNull();
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("ProductConstraintsViolationException — second ctor accepts a Set<ConstraintViolation<Object>>")
        void productConstraintsViolationException() {
            final Set<ConstraintViolation<Object>> violations = Set.of();
            final ProductConstraintsViolationException ex =
                    new ProductConstraintsViolationException("bad", violations);

            assertThat(ex.getMessage()).isEqualTo("bad");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("NotEnoughStockException — domain semantics (BUG-061 reference)")
    class NotEnoughStockSemantics {

        @Test
        @DisplayName("message preserved verbatim — no productUuid/requested/available getters surface today")
        void messageIsLiteral() {
            final NotEnoughStockException ex = new NotEnoughStockException("Not enough stock");

            // No domain getters per BUG-061 — the message is the only carrier of operator info today.
            assertThat(ex.getMessage()).isEqualTo("Not enough stock");
            assertThat(Arrays.stream(ex.getClass().getDeclaredMethods())
                    .map(java.lang.reflect.Method::getName))
                    .as("no extra getters yet (BUG-061 — fix would add productUuid/requested/available)")
                    .doesNotContain("getProductUuid", "getRequested", "getAvailable");
        }
    }

    @Nested
    @DisplayName("Sealed QuantityChangeResult hierarchy + QuantityRejectionReason enum")
    class QuantityHierarchy {

        @Test
        @DisplayName("QuantityChangeResult is sealed and permits exactly QuantityUpdated + QuantityRejected")
        void sealedHierarchyIsClosed() {
            assertThat(QuantityChangeResult.class.isSealed()).isTrue();

            final Set<Class<?>> permitted = Arrays.stream(QuantityChangeResult.class.getPermittedSubclasses())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertThat(permitted).containsExactlyInAnyOrder(QuantityUpdated.class, QuantityRejected.class);
        }

        @Test
        @DisplayName("QuantityUpdated record carries newQuantity")
        void quantityUpdatedRecord() {
            final QuantityUpdated u = new QuantityUpdated(7);
            assertThat(u.newQuantity()).isEqualTo(7);
            assertThat(u).isInstanceOf(QuantityChangeResult.class);
        }

        @Test
        @DisplayName("QuantityRejected record carries the rejection reason enum")
        void quantityRejectedRecord() {
            final QuantityRejected r = new QuantityRejected(QuantityRejectionReason.QUANTITY_LOWER_THAN_ZERO);
            assertThat(r.reason()).isEqualTo(QuantityRejectionReason.QUANTITY_LOWER_THAN_ZERO);
            assertThat(r).isInstanceOf(QuantityChangeResult.class);
        }

        @Test
        @DisplayName("QuantityRejectionReason enum exposes both documented constants")
        void enumValues() {
            assertThat(QuantityRejectionReason.values())
                    .containsExactlyInAnyOrder(
                            QuantityRejectionReason.AMOUNT_TO_DECREASE_BIGGER_THAN_CURRENT_QUANTITY,
                            QuantityRejectionReason.QUANTITY_LOWER_THAN_ZERO);
        }

        @Test
        @DisplayName("pattern-matching switch over the sealed hierarchy is exhaustive")
        void patternMatchingSwitchIsExhaustive() {
            assertThat(describe(new QuantityUpdated(3))).isEqualTo("updated:3");
            assertThat(describe(new QuantityRejected(QuantityRejectionReason.QUANTITY_LOWER_THAN_ZERO)))
                    .isEqualTo("rejected:QUANTITY_LOWER_THAN_ZERO");
            assertThat(describe(new QuantityRejected(QuantityRejectionReason.AMOUNT_TO_DECREASE_BIGGER_THAN_CURRENT_QUANTITY)))
                    .isEqualTo("rejected:AMOUNT_TO_DECREASE_BIGGER_THAN_CURRENT_QUANTITY");
        }

        // Pattern-matching switch over a sealed interface — Java 26 + preview (Spring Boot 4 / java.version=26).
        // No default branch needed because the sealed permits enumerate the universe.
        private String describe(final QuantityChangeResult result) {
            return switch (result) {
                case QuantityUpdated u -> "updated:" + u.newQuantity();
                case QuantityRejected r -> "rejected:" + r.reason().name();
            };
        }
    }

    @Nested
    @DisplayName("Spot-checks on selected high-impact exceptions")
    class SpotChecks {

        @Test
        @DisplayName("OrderNotFoundException preserves message and cause via both ctors")
        void orderNotFoundException() {
            final OrderNotFoundException msgOnly = new OrderNotFoundException("order=42");
            assertThat(msgOnly.getMessage()).isEqualTo("order=42");
            assertThat(msgOnly.getCause()).isNull();

            final IllegalStateException root = new IllegalStateException("repo down");
            final OrderNotFoundException wrapped = new OrderNotFoundException("order=42", root);
            assertThat(wrapped.getMessage()).isEqualTo("order=42");
            assertThat(wrapped.getCause()).isSameAs(root);
        }

        @Test
        @DisplayName("BankCardExpiredException is throwable and catches as itself")
        void bankCardExpiredException() {
            assertThatThrownBy(() -> { throw new BankCardExpiredException("expired"); })
                    .isInstanceOf(BankCardExpiredException.class)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("expired");
        }

        @Test
        @DisplayName("PaymentFailedException — single (String) ctor (BUG-136 documented)")
        void paymentFailedExceptionSingleCtor() {
            final PaymentFailedException ex = new PaymentFailedException("declined");
            assertThat(ex.getMessage()).isEqualTo("declined");
            assertThat(ex.getCause()).isNull();
            assertThat(hasCtor(PaymentFailedException.class, String.class, Throwable.class))
                    .as("BUG-136 — PaymentFailedException is missing (String, Throwable) ctor")
                    .isFalse();
        }

        @Test
        @DisplayName("UserAlreadyExistsException — single (String) ctor (BUG-136 documented)")
        void userAlreadyExistsExceptionSingleCtor() {
            final UserAlreadyExistsException ex = new UserAlreadyExistsException("dup");
            assertThat(ex.getMessage()).isEqualTo("dup");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("CartNotFoundException supports cause-wrapping")
        void cartNotFoundExceptionWraps() {
            final IllegalArgumentException root = new IllegalArgumentException("null uuid");
            final CartNotFoundException wrapped = new CartNotFoundException("cart missing", root);
            assertThat(wrapped.getCause()).isSameAs(root);
        }

        @Test
        @DisplayName("NegativeQuantityException carries BUG-039 javadoc context (round-trip)")
        void negativeQuantityException() {
            final NegativeQuantityException ex = new NegativeQuantityException("qty=-3");
            assertThat(ex.getMessage()).isEqualTo("qty=-3");
            assertThat(ex).isInstanceOf(RuntimeException.class);

            final NegativeQuantityException wrapped =
                    new NegativeQuantityException("qty=-3", new IllegalStateException("inner"));
            assertThat(wrapped.getCause()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("UnauthorizedBankCardAccessException carries BUG-038 javadoc context (round-trip)")
        void unauthorizedBankCardAccessException() {
            final UnauthorizedBankCardAccessException ex =
                    new UnauthorizedBankCardAccessException("card=xyz user=u1");
            assertThat(ex.getMessage()).isEqualTo("card=xyz user=u1");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
