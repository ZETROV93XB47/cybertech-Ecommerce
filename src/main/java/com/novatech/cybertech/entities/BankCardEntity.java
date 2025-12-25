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
@ToString(callSuper = true, exclude = {"userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"userEntity"})
public class BankCardEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER,cascade=CascadeType.ALL)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity userEntity;

    @Column(name = "cardHolderName", nullable = false, length = 100)
    private String cardHolderName;

    @Column(name = "cardNumber", nullable = false, length = 19) // Longueur typique avec espaces/tirets
    private String cardNumber; // Stocker comme String pour gérer les zéros initiaux et le formatage

    @Column(name = "expiryDate", nullable = false, length = 7) // Format MM/YYYY
    private String expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "cardType", nullable = false)
    private BankCardType cardType;

    @Column(name = "isDefault", nullable = false)
    private Boolean isDefault = false;
}