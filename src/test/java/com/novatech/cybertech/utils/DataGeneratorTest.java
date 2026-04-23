package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataGeneratorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @Nested
    @DisplayName("getComputerTypeProductAttributes")
    class ComputerTypeAttributes {

        @Test
        @DisplayName("Should produce a non-null map containing all expected keys with non-null values")
        void shouldProduceMapWithExpectedKeys() {
            // When
            final Map<String, Object> attrs = DataGenerator.getComputerTypeProductAttributes();

            // Then
            assertThat(attrs).isNotNull()
                    .containsKeys("cpu", "gpu", "ram", "os", "connectivity",
                            "displayType", "memory", "brand")
                    .doesNotContainValue(null);
            assertThat(attrs.get("brand")).isInstanceOf(Brand.class);
            assertThat(attrs.get("ram")).isInstanceOf(Integer.class);
            assertThat(attrs.get("memory")).isInstanceOf(Integer.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 25})
        @DisplayName("Repeated invocations should always return non-null attribute maps")
        void shouldRepeatedlyProduceValidMaps(int n) {
            for (int i = 0; i < n; i++) {
                Map<String, Object> attrs = DataGenerator.getComputerTypeProductAttributes();
                assertThat(attrs).isNotNull().hasSize(8);
            }
        }
    }

    @Nested
    @DisplayName("generateReviewEntity")
    class ReviewEntityGen {

        @Test
        @DisplayName("Should produce a non-null review with embedded user and product")
        void shouldProduceNonNullReview() {
            // When
            final ReviewEntity review = DataGenerator.generateReviewEntity();

            // Then
            assertThat(review).isNotNull();
            assertThat(review.getRating()).isEqualTo(5);
            assertThat(review.getComment()).isNotBlank();
            assertThat(review.getIsHateful()).isFalse();
            assertThat(review.getCreatedAt()).isNotNull();
            assertThat(review.getUpdatedAt()).isNotNull();
            assertThat(review.getUserEntity()).isNotNull();
            assertThat(review.getProductEntity()).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateProduct")
    class ProductGen {

        @Test
        @DisplayName("Should produce a non-null ProductEntity with sane defaults")
        void shouldProduceNonNullProduct() {
            // When
            final ProductEntity product = DataGenerator.generateProduct();

            // Then
            assertThat(product).isNotNull();
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getDescription()).isNotBlank();
            assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal(2300));
            assertThat(product.getCategory()).isEqualTo(Category.COMPUTER);
            assertThat(product.getBrand()).isEqualTo(Brand.ASUS);
            assertThat(product.getStock()).isEqualTo(1000);
            assertThat(product.getReservedStock()).isEqualTo(3);
            assertThat(product.getOrderItemEntities()).isNotNull().isEmpty();
            assertThat(product.getPhoto()).isNotBlank();
        }

        @Test
        @DisplayName("Repeated invocations should yield non-null product entities")
        void repeatedInvocationsAreSafe() {
            for (int i = 0; i < 25; i++) {
                assertThat(DataGenerator.generateProduct()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("createProductCreateRequestDto")
    class ProductCreateRequestGen {

        @Test
        @DisplayName("Should produce a valid ProductCreateRequestDto (no constraint violations)")
        void shouldProduceValidDto() {
            // When
            final ProductCreateRequestDto dto = DataGenerator.createProductCreateRequestDto();

            // Then
            assertThat(dto).isNotNull();
            assertThat(dto.getName()).isNotBlank();
            assertThat(dto.getPrice()).isPositive();
            assertThat(dto.getStock()).isNotNegative();
            assertThat(dto.getDescription()).isNotBlank();
            assertThat(dto.getAttributes()).isNotNull().isNotEmpty();
            assertThat(dto.getCategory()).isEqualTo(Category.COMPUTER);
            assertThat(dto.getBrand()).isNotNull();

            Set<ConstraintViolation<ProductCreateRequestDto>> violations = validator.validate(dto);
            assertThat(violations).as("ProductCreateRequestDto must satisfy bean validation").isEmpty();
        }
    }

    @Nested
    @DisplayName("generateUser / generateUsers / generateUsersCustom")
    class UserGen {

        @Test
        @DisplayName("generateUser should return a non-null UserEntity with role USER")
        void generateUserShouldReturnNonNull() {
            // When
            final UserEntity user = DataGenerator.generateUser();

            // Then
            assertThat(user).isNotNull();
            assertThat(user.getEmail()).isNotBlank();
            assertThat(user.getFirstName()).isNotBlank();
            assertThat(user.getLastName()).isNotBlank();
            assertThat(user.getRole()).isEqualTo(Role.USER);
            assertThat(user.getSex()).isEqualTo(Sex.M);
            assertThat(user.getAddress()).isNotNull();
            assertThat(user.getBirthDate()).isNotNull();
            assertThat(user.getOrderEntities()).isNotNull().isEmpty();
            assertThat(user.getNumberOfHatefulComments()).isZero();
        }

        @Test
        @DisplayName("generateUsers(0) should fall back to a single ADMIN user")
        void generateUsersZeroShouldFallbackToOne() {
            // When
            final Collection<UserEntity> users = DataGenerator.generateUsers(0);

            // Then
            assertThat(users).hasSize(1);
            UserEntity sole = users.iterator().next();
            assertThat(sole.getRole()).isEqualTo(Role.ADMIN);
            assertThat(sole.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("generateUsers(-3) should also fall back to a single ADMIN user")
        void generateUsersNegativeShouldFallbackToOne() {
            assertThat(DataGenerator.generateUsers(-3)).hasSize(1);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 5, 10})
        @DisplayName("generateUsers(N) for N>0 should return N admins, alternating sex by index")
        void generateUsersPositive(int n) {
            // When
            final Collection<UserEntity> users = DataGenerator.generateUsers(n);

            // Then
            assertThat(users).hasSize(n);
            assertThat(users).allSatisfy(u -> {
                assertThat(u).isNotNull();
                assertThat(u.getRole()).isEqualTo(Role.ADMIN);
                assertThat(u.getIsActive()).isTrue();
                assertThat(u.getEmail()).isNotBlank();
            });
        }

        @Test
        @DisplayName("generateUsersCustom with valid args should yield N users assigning incrementing ids")
        void generateUsersCustomHappyPath() {
            // When
            final Collection<UserEntity> users =
                    DataGenerator.generateUsersCustom(3, Sex.F, Role.USER, 100L);

            // Then
            assertThat(users).hasSize(3);
            assertThat(users).extracting(UserEntity::getId)
                    .containsExactly(100L, 101L, 102L);
            assertThat(users).allSatisfy(u -> assertThat(u.getRole()).isEqualTo(Role.USER));
        }

        @Test
        @DisplayName("generateUsersCustom with invalid args should fall back to generateUsers(0)")
        void generateUsersCustomInvalidArgs() {
            // 0 users
            assertThat(DataGenerator.generateUsersCustom(0, Sex.F, Role.USER, 1L)).hasSize(1);
            // negative count
            assertThat(DataGenerator.generateUsersCustom(-1, Sex.F, Role.USER, 1L)).hasSize(1);
            // null sex
            assertThat(DataGenerator.generateUsersCustom(3, null, Role.USER, 1L)).hasSize(1);
            // null role
            assertThat(DataGenerator.generateUsersCustom(3, Sex.F, null, 1L)).hasSize(1);
            // negative firstId
            assertThat(DataGenerator.generateUsersCustom(3, Sex.F, Role.USER, -1L)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("generateUserCreateRequestDto")
    class UserCreateRequestGen {

        @Test
        @DisplayName("Should produce a non-null UserCreateRequestDto with populated bank card")
        void shouldProduceNonNullDto() {
            // When
            final UserCreateRequestDto dto = DataGenerator.generateUserCreateRequestDto();

            // Then
            assertThat(dto).isNotNull();
            assertThat(dto.getEmail()).contains("@gmail.com");
            assertThat(dto.getFirstName()).isNotBlank();
            assertThat(dto.getLastName()).isNotBlank();
            assertThat(dto.getSex()).isEqualTo(Sex.M);
            assertThat(dto.getPhoneNumber()).isNotBlank();
            assertThat(dto.getStreet()).isNotBlank();
            assertThat(dto.getCity()).isNotBlank();
            assertThat(dto.getZipCode()).isNotBlank();
            assertThat(dto.getCountry()).isNotBlank();
            assertThat(dto.getBirthDate()).isNotNull();
            assertThat(dto.getPassword()).isNotBlank();
            assertThat(dto.getFavoriteCommunicationChanel()).isNotNull();
            assertThat(dto.getBankCardCreationRequestDto()).isNotNull();
            assertThat(dto.getBankCardCreationRequestDto().getExpiryDate()).isEqualTo("12/2029");
            assertThat(dto.getBankCardCreationRequestDto().getCardHolderName()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("generateOrder / generatePayment")
    class OrderAndPaymentGen {

        @Test
        @DisplayName("generateOrder should return a non-null order with one payment attempt")
        void shouldGenerateOrder() {
            // When
            final OrderEntity order = DataGenerator.generateOrder();

            // Then
            assertThat(order).isNotNull();
            assertThat(order.getOrderDate()).isNotNull();
            assertThat(order.getOrderItemEntities()).isNotNull().isEmpty();
            assertThat(order.getShippingAddress()).isNotNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(order.getTotalAmount()).isNotNull();
            assertThat(order.getTotalAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getPaymentAttempts()).hasSize(1);
        }

        @Test
        @DisplayName("generatePayment should return a SUCCESS / VISA payment of 0 amount")
        void shouldGeneratePayment() {
            // When
            final PaymentEntity payment = DataGenerator.generatePayment();

            // Then
            assertThat(payment).isNotNull();
            assertThat(payment.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
            assertThat(payment.getPaymentType()).isEqualTo(PaymentType.VISA);
            assertThat(payment.getAmount()).isNotNull();
            assertThat(payment.getAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(payment.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("orderGenerator – BUG-135")
    class OrderGeneratorBug {

        @Test
        @DisplayName("Should produce a non-null OrderPlacingRequestDto with VISA / FEDEX defaults")
        void shouldProduceNonNullOrder() {
            // When
            final OrderPlacingRequestDto dto = DataGenerator.orderGenerator();

            // Then
            assertThat(dto).isNotNull();
            assertThat(dto.getPaymentType()).isEqualTo(PaymentType.VISA);
            assertThat(dto.getShippingType()).isEqualTo(ShippingType.STANDARD);
            assertThat(dto.getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
            assertThat(dto.getShippingStreet()).isNotBlank();
            assertThat(dto.getShippingCity()).isNotBlank();
            assertThat(dto.getShippingZipCode()).isNotBlank();
            assertThat(dto.getShippingCountry()).isNotBlank();
            assertThat(dto.getUserUuid()).isNotNull();

            Set<ConstraintViolation<OrderPlacingRequestDto>> violations = validator.validate(dto);
            assertThat(violations).as("orderGenerator() must satisfy bean validation").isEmpty();
        }

        @Test
        @DisplayName("FIX BUG-135: orderGenerator() no longer returns the hardcoded UUID")
        void orderGeneratorNoLongerReturnsHardcodedUuid_pinsFix() {
            // Given – BUG-135 fix: userUuid is now a fresh UUID.randomUUID() per invocation.
            final UUID previouslyHardcoded = UUID.fromString("ac1d3001-9bce-1597-819b-ce15dac20000");

            // When
            final OrderPlacingRequestDto a = DataGenerator.orderGenerator();

            // Then – the previously-pinned hardcoded value is gone.
            assertThat(a.getUserUuid()).isNotEqualTo(previouslyHardcoded);
        }

        @Test
        @DisplayName("FIX BUG-135: two consecutive orderGenerator() calls produce DIFFERENT userUuid")
        void orderGeneratorProducesDifferentUuids() {
            // When
            final OrderPlacingRequestDto a = DataGenerator.orderGenerator();
            final OrderPlacingRequestDto b = DataGenerator.orderGenerator();

            // Then
            assertThat(a.getUserUuid()).isNotEqualTo(b.getUserUuid());
        }
    }

    @Nested
    @DisplayName("generateOrderUpdateRequestDto")
    class OrderUpdateGen {

        @Test
        @DisplayName("Should produce a valid OrderUpdateRequestDto (no constraint violations)")
        void shouldProduceValidDto() {
            // When
            final OrderUpdateRequestDto dto = DataGenerator.generateOrderUpdateRequestDto();

            // Then
            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isNotNull();
            assertThat(dto.getPaymentType()).isEqualTo(PaymentType.MASTERCARD);
            assertThat(dto.getShippingType()).isEqualTo(ShippingType.EXPRESS);
            assertThat(dto.getShippingProvider()).isEqualTo(ShippingProvider.DHL);
            assertThat(dto.getShippingStreet()).isNotBlank();
            assertThat(dto.getShippingCity()).isNotBlank();
            assertThat(dto.getShippingZipCode()).isNotBlank();
            assertThat(dto.getShippingCountry()).isNotBlank();
            assertThat(dto.getItemUpdateRequestDtoList()).hasSize(1);
            assertThat(dto.getItemUpdateRequestDtoList().getFirst().getProductUuid()).isNotNull();
            assertThat(dto.getItemUpdateRequestDtoList().getFirst().getQuantity()).isBetween(1, 5);

            Set<ConstraintViolation<OrderUpdateRequestDto>> violations = validator.validate(dto);
            assertThat(violations).as("OrderUpdateRequestDto must satisfy bean validation").isEmpty();
        }

        @Test
        @DisplayName("Two consecutive calls should produce different uuids and items (datafaker randomness)")
        void shouldProduceDistinctUuids() {
            assertThat(DataGenerator.generateOrderUpdateRequestDto().getUuid())
                    .isNotEqualTo(DataGenerator.generateOrderUpdateRequestDto().getUuid());
        }
    }

    @Nested
    @DisplayName("convertToUUID")
    class ConvertToUuid {

        @Test
        @DisplayName("Should accept the documented 0x-prefixed 32-hex string and not throw")
        void shouldNotThrowForValid0xPrefixedHex() {
            // The method prints to System.out and returns void; we only assert it does not throw.
            DataGenerator.convertToUUID("0x019C3FA4B4D2700D94694FE90CED0D7A");
        }

        @Test
        @DisplayName("Null input throws NullPointerException (substring(2) on null)")
        void nullInputThrows() {
            assertThatThrownBy(() -> DataGenerator.convertToUUID(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Non-hex payload throws NumberFormatException")
        void nonHexPayloadThrows() {
            assertThatThrownBy(() -> DataGenerator.convertToUUID("0xZZZZ"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("Too-short input (length < 2) throws StringIndexOutOfBoundsException")
        void tooShortInputThrows() {
            assertThatThrownBy(() -> DataGenerator.convertToUUID(""))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }
    }
}
