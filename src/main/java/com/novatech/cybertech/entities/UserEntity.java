package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@SuperBuilder
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "userTable")
@ToString(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntities"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderEntities", "reviewEntities", "bankCardEntities"})
public class UserEntity extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 50)
    private String email;

    @Column(name = "firstName", length = 50)
    private String firstName;

    @Column(name = "lastName", length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false)
    private Sex sex;

    @Column(name = "address", length = 255)
    private String address;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "birthDate")
    private LocalDateTime birthDate;

    @Column(name = "password", length = 100)
    private String password;

    @Column(unique = true, nullable = false)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "phoneNumber")
    private String phoneNumber;

    @Column(name = "isActive", nullable = false)
    private Boolean isActive = true;

    //@Enumerated(EnumType.STRING)
    @Column(name = "defaultCommunicationChanel", nullable = false)
    private CommunicationChanel favoriteCommunicationChanel;

    @Column(name = "numberOfHatefulComments", nullable = false)
    private int numberOfHatefulComments = 0;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderEntity> orderEntities;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewEntity> reviewEntities;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BankCardEntity> bankCardEntities;
}

//@OneToMany(fetch = FetchType.EAGER, mappedBy = "userEntity", cascade = CascadeType.ALL, orphanRemoval = true)
//private List<CartEntity> cartEntities;
