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
@ToString(callSuper = true, exclude = {"userEntity", "encryptedNumber"})
@EqualsAndHashCode(callSuper = true, exclude = {"userEntity"})
public class BankCardEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Column(name = "cardHolderName", nullable = false, length = 100)
    private String cardHolderName;

    /**
     * At-rest AES/GCM ciphertext of the PAN (base64-encoded IV + ciphertext + tag).
     *
     * <p>Length is generous (512) because the envelope includes a random per-record IV and a
     * GCM authentication tag — see {@code AesCardEncryptionService} for the full rationale.
     * Never returned in any response DTO.</p>
     */
    @Column(name = "encryptedNumber", length = 512)
    private String encryptedNumber;

    /**
     * Last four digits of the PAN, cached for display. This is the ONLY PAN-derived
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
}
