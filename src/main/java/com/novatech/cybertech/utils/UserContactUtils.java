package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.UserEntity;

/**
 * Builds the {@link UserContactDto} every notification path needs (order confirmation, payment
 * confirmation, shipping confirmation) from a {@link UserEntity}. Used to be copy-pasted in each
 * listener / tasklet.
 */
public final class UserContactUtils {

    private UserContactUtils() {
        // utility class — no instantiation
    }

    public static UserContactDto toUserContact(final UserEntity user) {
        return UserContactDto.builder()
                .email(user.getEmail())
                .name(user.getFirstName())
                .phoneNumber(user.getPhoneNumber())
                .defaultCommunicationChanel(user.getFavoriteCommunicationChanel())
                .build();
    }
}
