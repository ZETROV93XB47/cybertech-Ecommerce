package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "pushSubscriptionTable",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint_hash", columnNames = "endpointHash"),
        indexes = @Index(name = "idx_push_subscription_user", columnList = "userId")
)
@ToString(callSuper = true, exclude = {"endpointEncrypted", "p256dhEncrypted", "authEncrypted", "userEntity"})
@EqualsAndHashCode(callSuper = true, exclude = {"endpointEncrypted", "p256dhEncrypted", "authEncrypted", "userEntity"})
public class PushSubscriptionEntity extends BaseEntity<Long> {

    @Column(name = "endpointHash", nullable = false, length = 64)
    private String endpointHash;

    @Lob
    @Column(name = "endpointEncrypted", nullable = false, columnDefinition = "TEXT")
    private String endpointEncrypted;

    @Column(name = "p256dhEncrypted", nullable = false, length = 512)
    private String p256dhEncrypted;

    @Column(name = "authEncrypted", nullable = false, length = 512)
    private String authEncrypted;

    @Column(name = "expirationTime")
    private Instant expirationTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false)
    private UserEntity userEntity;
}
