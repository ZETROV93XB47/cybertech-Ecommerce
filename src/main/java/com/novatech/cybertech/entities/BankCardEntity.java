package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.BankCardType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bankCardTable")
@ToString(callSuper = true, exclude = {"userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"userEntity"})
public class BankCardEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Column(name = "cardHolderName", nullable = false, length = 100)
    private String cardHolderName;

    /**
     * Stripe PaymentMethod ({@code pm_...}) of the card, attached to the user's Customer. Every
     * payment is charged on it. The card number itself is entered in Stripe Elements and is never
     * received nor stored by the backend.
     *
     * <p>Nullable only for cards saved before the Stripe flow (they held an encrypted number and
     * cannot be charged): they are purged by {@code sql/migrations/2026-10-14-stripe-saved-card.sql}
     * and treated as "no card" until then.</p>
     */
    @Column(name = "stripePaymentMethodId", length = 255, unique = true)
    private String stripePaymentMethodId;

    /**
     * Last four digits as reported by Stripe, for display. This is the ONLY card-number-derived
     * value that may ever appear in a response, and only embedded in a masked form
     * ({@code "**** **** **** 4242"}).
     */
    @Column(name = "lastFourDigits", length = 4)
    private String lastFourDigits;

    @Column(name = "expiryDate", nullable = false, length = 7) // Format MM/YYYY
    private String expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "cardType", nullable = false)
    private BankCardType cardType;

    /** {@code true} when the card can be charged (saved through Stripe, not a legacy row). */
    public boolean isChargeable() {
        return stripePaymentMethodId != null && !stripePaymentMethodId.isBlank();
    }
}
