package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.BankCardType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "bankCardTable")
@ToString(callSuper = true, exclude = {"userEntity", "encryptedNumber", "cardNumber"})
@EqualsAndHashCode(callSuper = true, exclude = {"userEntity"})
public class BankCardEntity extends BaseEntity<Long> {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Column(name = "cardHolderName", nullable = false, length = 100)
    private String cardHolderName;

    /**
     * <b>BUG-036 (PCI-DSS) — legacy column, kept for backward compatibility only.</b>
     *
     * <p>Populated only by legacy rows and historical test fixtures; new code writes the PAN
     * through {@link #encryptedNumber} instead and never copies the plaintext here. Kept as a
     * nullable column so existing database rows do not trip the schema validator and so
     * fixtures that pre-date the fix still compile.</p>
     *
     * @deprecated Do not write to this field from new code. Use {@link #encryptedNumber} +
     *             {@link #lastFourDigits} for all new persistence paths.
     */
    @Deprecated
    @Column(name = "cardNumber", length = 25)
    private String cardNumber;

    /**
     * BUG-036: at-rest AES/GCM ciphertext of the PAN (base64-encoded IV + ciphertext + tag).
     *
     * <p>Length is generous (512) because the envelope includes a random per-record IV and a
     * GCM authentication tag — see {@code AesCardEncryptionService} for the full rationale.
     * Never returned in any response DTO.</p>
     */
    @Column(name = "encryptedNumber", length = 512)
    private String encryptedNumber;

    /**
     * BUG-036: last four digits of the PAN, cached for display. This is the ONLY PAN-derived
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

    /**
     * BUG-038: marks the user's default bank card. Only one card per user should carry
     * {@code true}; the service layer enforces this invariant on {@code setDefault}.
     *
     * <p>Named {@code isDefault} (Boolean wrapper) so Lombok's generated accessors become
     * {@code getIsDefault()}/{@code setIsDefault(Boolean)} — this keeps MapStruct happy
     * (property name stays {@code isDefault}) while remaining non-null in the DB via the
     * column-level constraint plus builder default.</p>
     */
    @Column(name = "isDefault", nullable = false)
    private Boolean isDefault;
}
