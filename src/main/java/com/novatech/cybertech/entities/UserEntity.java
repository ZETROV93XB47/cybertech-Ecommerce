package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.valueObjects.Address;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "userTable")
@ToString(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntity", "cartEntities"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntity", "cartEntities"})
public class UserEntity extends BaseEntity<Long> {

    @Column(name = "email", nullable = false, unique = true, length = 50)
    private String email;

    @Column(name = "firstName", length = 50)
    private String firstName;

    @Column(name = "lastName", length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false)
    private Sex sex;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "address_street")),
            @AttributeOverride(name = "city", column = @Column(name = "address_city")),
            @AttributeOverride(name = "zipCode", column = @Column(name = "address_zip_code")),
            @AttributeOverride(name = "country", column = @Column(name = "address_country"))
    })
    private Address address;

    //TODO: Vérifier le type LocalDate pour la colonne birthDate
    @Column(name = "birthDate", columnDefinition = "DATE")
    private LocalDateTime birthDate;

    @Column(unique = true, nullable = false)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "phoneNumber")
    private String phoneNumber;

    @Column(name = "isActive", nullable = false)
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "defaultCommunicationChanel", nullable = false)
    private CommunicationChanel favoriteCommunicationChanel;

    @Column(name = "numberOfHatefulComments", nullable = false)
    private int numberOfHatefulComments = 0;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderEntity> orderEntities;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewEntity> reviewEntities;

    @OneToOne(mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private BankCardEntity bankCardEntity;

    @OneToOne(mappedBy = "userEntity", cascade = CascadeType.ALL)
    private CartEntity cartEntity;
}