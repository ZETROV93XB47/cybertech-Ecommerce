package com.novatech.cybertech.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.datafaker.Faker;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.entities.*;
import com.novatech.cybertech.entities.enums.*;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.novatech.cybertech.entities.enums.CommunicationChanel.EMAIL;
import static com.novatech.cybertech.entities.enums.Role.ADMIN;
import static com.novatech.cybertech.entities.enums.Role.USER;
import static com.novatech.cybertech.entities.enums.Sex.M;

@Slf4j
public class DataGenerator {

    private static final Faker FAKER = new Faker();

    private static final List<String> productCpu = List.of("AMD Ryzen 5 3550H", "Intel Core i7 9750H", "Intel Core i9 9880H", "Intel Core i7 8750H", "Intel Core i5 9300H", "Intel Core i5 8300H", "Intel Core i7 7700HQ", "Intel Core i7 9700K", "AMD Ryzen 7 3750H", "Intel Core i7 8700", "Intel Core i9 9880H", "Intel Core i5 10210U", "Intel Core i7 10710U", "Intel Core i5 8265U", "Intel Celeron N4000", "Intel Celeron N3350", "Intel Pentium Gold 4415Y", "Intel Core i5 8200Y", "Intel Pentium Silver N5000", "Intel Core i3 6006U", "Intel Core i5 8265U", "AMD Ryzen 5 3500U");
    private static final List<String> productOS = List.of("Windows 10 ", "Sans OS ", "Windows 11", "Linux", "MacOS");
    private static final List<String> productGpu = List.of("NVIDIA GeForce GTX 1660 Ti", "AMD Radeon RX 5703", "NVIDIA GeForce RTX 3060", "AMD Radeon RX 5600 XT", "NVIDIA GeForce GTX 1650", "Intel HD Graphics 620", "Intel HD Graphics 615", "Intel HD Graphics 610", "Intel HD Graphics 520", "Intel HD Graphics 605", "Intel HD Graphics 515", "Intel HD Graphics 500", "Intel HD Graphics 505", "Intel Iris Plus Graphics", "NVIDIA GeForce GTX 1660 Ti 6 Go", "NVIDIA GeForce RTX 2070 8 Go", "NVIDIA GeForce RTX 2080 8 Go", "NVIDIA GeForce GTX 1650 4 Go", "NVIDIA GeForce RTX 2060 6 Go", "NVIDIA GeForce GTX 1070 8 Go", "NVIDIA GeForce GTX 1080 8 Go", "NVIDIA GeForce GTX 1050 Ti 4 Go", "NVIDIA GeForce GTX 1050 2 Go", "NVIDIA GeForce GTX 1060 3 Go", "NVIDIA GeForce GTX 1060 6 Go", "AMD Radeon 520", "AMD Radeon 530", "AMD Radeon R2", "AMD Radeon R3", "AMD Radeon R5", "AMD Radeon RX 5500M", "AMD Radeon RX 560X", "AMD Radeon RX Vega 10 Graphics");
    private static final List<Integer> productRam = List.of(8, 16, 32, 64, 128, 256, 512, 1024);
    private static final List<Integer> productMemory = List.of(128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536);
    private static final List<String> productNetwork = List.of("WiFi AC/Bluetooth", "WiFi AX/Bluetooth", "WiFi AC/Bluetooth/4G", "WiFi AX/Bluetooth/4G");
    private static final List<String> productDisplayType = List.of("VA", "LCD", "PVA", "AMVA", "AMVA+", "IPS", "TN", "OLED", "AMOLED");


    public static Map<String, Object> getComputerTypeProductAttributes() {
        final Random rand = new Random();

        return Map.of(
                "cpu", productCpu.get(rand.nextInt(productCpu.size()))
                , "gpu", productGpu.get(rand.nextInt(productGpu.size()))
                , "ram", productRam.get(rand.nextInt(productRam.size()))
                , "os", productOS.get(rand.nextInt(productOS.size()))
                , "connectivity", productNetwork.get(rand.nextInt(productNetwork.size()))
                , "displayType", productDisplayType.get(rand.nextInt(productDisplayType.size()))
                , "memory", productMemory.get(rand.nextInt(productMemory.size()))
                , "brand", Arrays.stream(Brand.values()).toList().get(rand.nextInt(Brand.values().length))
        );
    }

    public static ReviewEntity generateReviewEntity() {

        UserEntity userEntity = generateUser();

        return ReviewEntity.builder()
                .rating(5)
                .comment("This product is absolutely terrible, I want a refund!")
                .isHateful(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .userEntity(userEntity)
                .productEntity(generateProduct())
                .build();
    }

    public static ProductEntity generateProduct() {
        return ProductEntity.builder()
                .name(FAKER.commerce().productName())
                .description("Gaming PC Fireeeee")
                .price(new BigDecimal(2300))
                .category(Category.COMPUTER)
                .brand(Brand.ASUS)
                .reservedStock(Math.abs(3))
                .stock(1000)
                .photo(FAKER.internet().image())
                .orderItemEntities(List.of())
                .build();
    }

    public static ProductCreateRequestDto createProductCreateRequestDto() {

        Map<String, Object> computerTypeProductAttributes = getComputerTypeProductAttributes();
        ProductCreateRequestDto requestDto = ProductCreateRequestDto.builder()
                .name(computerTypeProductAttributes.get("brand") + " " + FAKER.commerce().productName())
                .price(new BigDecimal("1299.99"))
                .brand((Brand) computerTypeProductAttributes.get("brand"))
                .category(Category.COMPUTER)
                .photo("https://example.com/images/pc.jpg")
                .stock(15000)
                .description("Ordinateur portable performant avec processeur Intel et carte graphique NVIDIA.")
                .attributes(computerTypeProductAttributes)
                .build();

        return requestDto;
    }

    public static UserEntity generateUser() {
        return UserEntity.builder()
                .email(FAKER.internet().emailAddress())
                .firstName(FAKER.name().firstName())
                .lastName(FAKER.name().lastName())
                .sex(M)
                .address(new Address(FAKER.address().streetAddress(), FAKER.address().city(), FAKER.address().zipCode(), FAKER.address().country()))
                .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                .role(USER)
                .numberOfHatefulComments(0)
                .orderEntities(new ArrayList<>())
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
                    .address(new Address(FAKER.address().streetAddress(), FAKER.address().city(), FAKER.address().zipCode(), FAKER.address().country()))
                    .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                    .role(ADMIN)
                    .isActive(true)
                    .numberOfHatefulComments(0)
                    .orderEntities(new ArrayList<>())
                    .reviewEntities(new ArrayList<>())
                    .build();

            return List.of(user);

        } else {
            List<UserEntity> users = new ArrayList<>();

            for (long i = 1; i < numberOfUsers + 1; i++) {
                UserEntity userEntity = UserEntity.builder()
                        .email(FAKER.internet().emailAddress())
                        .firstName(FAKER.name().firstName())
                        .lastName(FAKER.name().lastName())
                        .sex(i % 2 == 0 ? Sex.F : M)
                        .address(new Address(FAKER.address().streetAddress(), FAKER.address().city(), FAKER.address().zipCode(), FAKER.address().country()))
                        .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                        .role(ADMIN)
                        .favoriteCommunicationChanel(EMAIL)
                        .isActive(true)
                        .numberOfHatefulComments(0)
                        .orderEntities(new ArrayList<>())
                        .reviewEntities(new ArrayList<>())
                        .build();

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
                .street(FAKER.address().streetAddress())
                .city(FAKER.address().city())
                .zipCode(FAKER.address().zipCode())
                .country(FAKER.address().country())
                .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                .password(FAKER.internet().password())
                .favoriteCommunicationChanel(EMAIL)
                .bankCardCreationRequestDto(BankCardCreationRequestDto.builder()
                        .cardHolderName(firstName + " " + lastName)
                        .cardNumber(FAKER.finance().creditCard())
                        .expiryDate("12/2029")
                        .cardType(BankCardType.VISA)
                        .build())
                .build();
    }

    public static OrderEntity generateOrder() {
        return OrderEntity.builder()
                .orderDate(LocalDateTime.now())
                .orderItemEntities(new ArrayList<>())
                .shippingAddress(new Address(FAKER.address().streetAddress(), FAKER.address().city(), FAKER.address().zipCode(), FAKER.address().country()))
                .status(OrderStatus.CREATED)
                .totalAmount(new Money(BigDecimal.ZERO, CurrencyCode.EUR))
                .paymentAttempts(List.of(generatePayment()))
                .build();
    }

    public static PaymentEntity generatePayment() {
        return PaymentEntity.builder()
                .amount(Money.of(new BigDecimal(0)))
                .createdAt(LocalDateTime.now())
                .status(PaymentAttemptStatus.SUCCESS)
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
                            .address(new Address(FAKER.address().streetAddress(), FAKER.address().city(), FAKER.address().zipCode(), FAKER.address().country()))
                            .birthDate(LocalDateTime.ofInstant(FAKER.date().birthday().toInstant(), ZoneId.systemDefault()))
                            .role(USER)
                            .orderEntities(new ArrayList<>())
                            .build()
            );
        }
        return users;
    }

    /**
     * Build a fully-populated {@link OrderPlacingRequestDto} suitable for happy-path
     * tests and demo data. Each invocation now produces a fresh {@code userUuid}
     * (mirroring {@link #generateOrderUpdateRequestDto()}); the previous hard-coded
     * UUID introduced cross-test coupling and was tracked as BUG-135.
     *
     * @return a freshly-built DTO carrying {@link PaymentType#VISA} / {@link ShippingType#STANDARD}
     *         / {@link ShippingProvider#FEDEX} defaults, or {@code null} if Jackson
     *         serialisation unexpectedly fails (preserved legacy contract).
     */
    public static OrderPlacingRequestDto orderGenerator() {
        OrderPlacingRequestDto build = OrderPlacingRequestDto.builder()
                .userUuid(UUID.randomUUID())
                .shippingStreet(FAKER.address().streetAddress())
                .shippingCity(FAKER.address().city())
                .shippingZipCode(FAKER.address().zipCode())
                .shippingCountry(FAKER.address().country())
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.FEDEX)
                .paymentType(PaymentType.VISA)
                .build();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValueAsString(build);
            return build;
        } catch (Exception e) {
            log.info("e :: ", e);
        }
        return null;
    }

    public static OrderUpdateRequestDto generateOrderUpdateRequestDto() {
        return OrderUpdateRequestDto.builder()
                .uuid(UUID.randomUUID())
                .paymentType(PaymentType.MASTERCARD)
                .shippingType(ShippingType.EXPRESS)
                .shippingProvider(ShippingProvider.DHL)
                .shippingStreet(FAKER.address().streetAddress())
                .shippingCity(FAKER.address().city())
                .shippingZipCode(FAKER.address().zipCode())
                .shippingCountry(FAKER.address().country())
                .itemUpdateRequestDtoList(List.of(
                        OrderItemCreateRequestDto.builder()
                                .productUuid(UUID.randomUUID())
                                .quantity(FAKER.number().numberBetween(1, 5))
                                .build()
                ))
                .build();
    }

    public static void convertToUUID(final String UUIDString) {
        String hex = UUIDString.substring(2);
        BigInteger b = new BigInteger(hex, 16);
        long mostSigBits = b.shiftRight(64).longValue();
        long leastSigBits = b.longValue();
        UUID uuid = new UUID(mostSigBits, leastSigBits);
        System.out.println(uuid);
    }

    static void dosmth(String[] args) {
        convertToUUID("0x019C3FA4B4D2700D94694FE90CED0D7A");
    }
}
