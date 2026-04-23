package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationChanelEnumTest {

    @Test
    void hasThreeChannels() {
        assertThat(CommunicationChanel.values()).containsExactly(
                CommunicationChanel.EMAIL,
                CommunicationChanel.SMS,
                CommunicationChanel.PUSH_NOTIFICATION);
    }

    @ParameterizedTest
    @EnumSource(CommunicationChanel.class)
    void everyChannelHasNonNullCode(final CommunicationChanel channel) {
        assertThat(channel.getCode()).isNotNull().isPositive();
    }

    @Test
    void canonicalCodeMapping() {
        assertThat(CommunicationChanel.EMAIL.getCode()).isEqualTo(1);
        assertThat(CommunicationChanel.SMS.getCode()).isEqualTo(2);
        assertThat(CommunicationChanel.PUSH_NOTIFICATION.getCode()).isEqualTo(3);
    }
}
