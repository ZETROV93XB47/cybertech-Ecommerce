package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.valueObjects.Address;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "userTable")
@ToString(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntity", "cartEntities", "wishlistItems", "recommendations"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntity", "cartEntities", "wishlistItems", "recommendations"})
public class UserEntity extends BaseEntity<Long> {

    @Column(name = "email", nullable = false, unique = true, length = 254)
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

    /**
     * <b>NON-AUTHORITATIVE — informational copy only.</b> Runtime authorization is decided
     * exclusively from the JWT realm roles issued by Keycloak (see
     * {@code KeycloakRoleConverter} + {@code @PreAuthorize("hasRole(...)")}): Keycloak is the
     * single source of truth for authorization. This column exists for display/reporting and
     * data-generation purposes only.
     *
     * <p><b>Never</b> branch security decisions on this field and <b>never</b> "promote" a user
     * by flipping it — doing either creates an authorization split-brain with the realm roles.
     * A role change must go through Keycloak (realm role mapping); this column may then be
     * refreshed as a cosmetic mirror.
     */
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

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<WishlistEntity> wishlistItems = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RecommendationEntity> recommendations = new HashSet<>();
}