package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.enums.BankCardType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Test fixture builder for {@link BankCardEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}. Default {@code expiryDate} is set 5 years in the future to
 * avoid spurious expiry validation failures.
 *
 * <p>The card is a chargeable Stripe card: it carries a {@code stripePaymentMethodId} (and the
 * default user a {@code stripeCustomerId}), never a card number.</p>
 */
public final class BankCardEntityBuilder {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    private BankCardEntityBuilder() {
    }

    public static BankCardEntity aValidBankCard() {
        return aValidBankCardBuilder().build();
    }

    public static BankCardEntity.BankCardEntityBuilder<?, ?> aValidBankCardBuilder() {
        return BankCardEntity.builder()
                .uuid(UUID.randomUUID())
                .cardHolderName("Jane Doe")
                .stripePaymentMethodId("pm_" + UUID.randomUUID().toString().replace("-", ""))
                .lastFourDigits("4242")
                .expiryDate(LocalDate.now().plusYears(5).format(EXPIRY_FORMAT))
                .cardType(BankCardType.VISA)
                .userEntity(UserEntityBuilder.aValidUser());
    }
}
