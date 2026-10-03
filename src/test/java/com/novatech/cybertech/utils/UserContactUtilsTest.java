package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContactUtilsTest {

    @Test
    void toUserContactCopiesEveryContactField() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().build();

        final UserContactDto contact = UserContactUtils.toUserContact(user);

        assertThat(contact.getEmail()).isEqualTo(user.getEmail());
        assertThat(contact.getName()).isEqualTo(user.getFirstName());
        assertThat(contact.getPhoneNumber()).isEqualTo(user.getPhoneNumber());
        assertThat(contact.getDefaultCommunicationChanel()).isEqualTo(user.getFavoriteCommunicationChanel());
    }
}
