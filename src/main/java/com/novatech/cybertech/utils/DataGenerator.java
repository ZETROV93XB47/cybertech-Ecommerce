package com.novatech.cybertech.utils;

import com.github.javafaker.Faker;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.entities.enums.CommunicationChanel.EMAIL;
import static com.novatech.cybertech.entities.enums.Role.ADMIN;
import static com.novatech.cybertech.entities.enums.Role.USER;
import static com.novatech.cybertech.entities.enums.Sex.M;

@Slf4j
public class DataGenerator {

    private static final Faker FAKER = new Faker();

    public static ReviewEntity generateReviewEntity() {

        UserEntity userEntity = generateUser();
        userEntity.setId((long) (Math.random() * 1000));

        return ReviewEntity.builder()
                //.id(new Random().nextLong())
                .rating(5)
                .comment("Fuck you nigga, i hope your family die from cancer")
                .isHateful(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .uuid(UUID.randomUUID())
                .userEntity(userEntity)
                .productEntity(generateProduct())
                .build();
    }

    public static ProductEntity generateProduct() {
        return ProductEntity.builder()
                //.id(new Random().nextLong())
                .uuid(UUID.randomUUID())
                .name(FAKER.commerce().productName())
                .description("Gaming PC Fireeeee")
                .price(new BigDecimal(2300))
//                .cpu("core ultra 7")
//                .gpu("RTX 5090 Ti")
//                .ram(Ram.GO_128)
                .category(Category.COMPUTER)
                .brand(Brand.ASUS)
                .reservedStock(Math.abs(3))
//                .connectivity("WIFI 7")
//                .displaySize(DisplaySize._15_INCHES)
//                .displayType(DisplayType.AMVA)
                .stock(1000)
//                .os(Os.WINDOWS)
//                .ssd(SSD.GO_8192)
                .photo(FAKER.internet().image())
                .orderItemEntities(List.of())
                //.reviewEntities(List.of())
                .build();
    }

    public static ProductCreateRequestDto createProductCreateRequestDto() {
        Map<String, Object> attributes = Map.of(
                "cpu", "Intel Core i7-11800H",
                "gpu", "NVIDIA RTX 3060",
                "ramGb", 16
        );

        ProductCreateRequestDto requestDto = ProductCreateRequestDto.builder()
                .name("Ordinateur Portable MKZ71")
                .price(new BigDecimal("1299.99"))
                .brand(Brand.DELL)
                .category(Category.COMPUTER)
                .photo("https://example.com/images/pc.jpg")
                .stock(15)
                .description("Ordinateur portable performant avec processeur Intel et carte graphique NVIDIA.")
                .attributes(attributes)
                .build();

        return requestDto;
    }

    public static UserEntity generateUser() {
        return UserEntity.builder()
                //.id(1875L)
                .email(FAKER.internet().emailAddress())
                .firstName(FAKER.name().firstName())
                .lastName(FAKER.name().lastName())
                .sex(M)
                .address(FAKER.address().streetAddress())
                .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                .password(FAKER.internet().password())
                .role(USER)
                .numberOfHatefulComments(0)
                .orderEntities(new ArrayList<>())
                .reviewEntities(new ArrayList<>())
                .build();
    }

    public static Collection<UserEntity> generateUsers(final int numberOfUsers) {

        final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        if (numberOfUsers <= 0) {
            log.info("Number of users is equal 0, only one user is going to be generated");
            UserEntity user = UserEntity.builder()
                    .email(FAKER.internet().emailAddress())
                    .firstName(FAKER.name().firstName())
                    .lastName(FAKER.name().lastName())
                    .sex(M)
                    .favoriteCommunicationChanel(EMAIL)
                    .address(FAKER.address().streetAddress())
                    .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                    .password(passwordEncoder.encode("password"))
                    .role(ADMIN)
                    .isActive(true)
                    .numberOfHatefulComments(0)
                    .orderEntities(new ArrayList<>())
                    //.cartEntities(new ArrayList<>())
                    .reviewEntities(new ArrayList<>())
                    .build();

            return List.of(user);

        }
        else {
            List<UserEntity> users = new ArrayList<>();

            for (long i = 1; i < numberOfUsers + 1; i++) {
                UserEntity userEntity = UserEntity.builder()
                        .uuid(UUID.randomUUID())
                        .email(FAKER.internet().emailAddress())
                        .firstName(FAKER.name().firstName())
                        .lastName(FAKER.name().lastName())
                        .sex(i % 2 == 0 ? Sex.F : M)
                        .address(FAKER.address().streetAddress())
                        .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                        .password(passwordEncoder.encode("password"))
                        .role(ADMIN)
                        .favoriteCommunicationChanel(EMAIL)
                        .isActive(true)
                        .numberOfHatefulComments(0)
                        .orderEntities(new ArrayList<>())
                        .reviewEntities(new ArrayList<>())
                        .bankCardEntities(new ArrayList<>())
                        //.cartEntities(new ArrayList<>())
                        .build();

                //ReviewEntity reviewEntity = generateReviewEntity(userEntity);
                //userEntity.getReviewEntities().add(reviewEntity);

                users.add(userEntity);

            }
            return users;
        }
    }

    public static UserCreateRequestDto generateUserCreateRequestDto() {
        String firstName = FAKER.name().firstName();
        String lastName = FAKER.name().lastName();

        return UserCreateRequestDto.builder()
                .email(firstName + "." + lastName + "@gmail.com")
                .firstName(firstName)
                .lastName(lastName)
                .sex(M)
                .phoneNumber(FAKER.phoneNumber().phoneNumber())
                .address(FAKER.address().streetAddress())
                .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                .password(FAKER.internet().password())
                .favoriteCommunicationChanel(EMAIL)
                .bankCardCreationRequestDto(BankCardCreationRequestDto.builder()
                        .cardHolderName(firstName + " " + lastName)
                        .cardNumber(FAKER.finance().creditCard())
                        .expiryDate("12/2029")
                        .cardType(BankCardType.VISA)
                        .isDefault(true)
                        .build())
                .build();
    }

    public static OrderEntity generateOrder() {
        return OrderEntity.builder()
                .uuid(UUID.randomUUID())
                .orderDate(LocalDateTime.now())
                .orderItemEntities(new ArrayList<>())
                .shippingAddress(FAKER.address().fullAddress())
                .status(OrderStatus.PROCESSING)
                .totalAmount(new BigDecimal(0))
                .paymentEntity(generatePayment())
                .build();
    }

    public static PaymentEntity generatePayment() {
        return PaymentEntity.builder()
                .uuid(UUID.randomUUID())
                .amount(new BigDecimal(0))
                .paymentDate(LocalDateTime.now())
                .paymentStatus(PaymentStatus.SUCCESS)
                .paymentType(PaymentType.VISA)
                .build();
    }

    public static Collection<UserEntity> generateUsersCustom(final int numberOfUsers, final Sex sex, final Role role, long firstId) {
        if (numberOfUsers <= 0 || sex == null || role == null || firstId < 0) {
            return generateUsers(0);
        }
        List<UserEntity> users = new ArrayList<>();
        for (long i = 0; i < numberOfUsers; i++) {
            users.add(
                    UserEntity.builder()
                            .id(firstId++)

                            .email(FAKER.internet().emailAddress())
                            .firstName(FAKER.name().firstName())
                            .lastName(FAKER.name().lastName())
                            .sex(i % 2 == 0 ? Sex.F : M)
                            .address(FAKER.address().streetAddress())
                            .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                            .password(FAKER.internet().password())
                            .role(USER)
                            .orderEntities(new ArrayList<>())
                            //.cartEntities(new ArrayList<>())
                            .build()
            );
        }
        return users;

    }

    public static OrderPlacingRequestDto orderGenerator() {
        UUID productUID = UUID.fromString("fd615b2b-85d8-43f4-8886-63e7489e5fc3");
        return OrderPlacingRequestDto.builder()
                .userUuid(UUID.fromString("cba8b420-97fe-4cd9-b63c-69bcfb551dfa"))
                .shippingAddress(FAKER.address().fullAddress())
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.FEDEX)
                .paymentType(PaymentType.VISA)
                .orderItems(List.of(OrderItemCreateRequestDto.builder().productUuid(productUID).quantity(1).build()))
                .build();

    }

//    public static OrderItemCreateRequestDto orderItemGenerator() {
//
//    }

}