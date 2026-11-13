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

    @Test
    void onlyEmailAndSmsCanBeChosenAsFavorite() {
        assertThat(CommunicationChanel.EMAIL.isUserSelectable()).isTrue();
        assertThat(CommunicationChanel.SMS.isUserSelectable()).isTrue();
        assertThat(CommunicationChanel.PUSH_NOTIFICATION.isUserSelectable()).isFalse();
    }

    @Test
    void favoriteOrDefaultKeepsSelectableChannelsAndFallsBackToEmail() {
        assertThat(CommunicationChanel.favoriteOrDefault(CommunicationChanel.SMS)).isEqualTo(CommunicationChanel.SMS);
        assertThat(CommunicationChanel.favoriteOrDefault(CommunicationChanel.EMAIL)).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(CommunicationChanel.favoriteOrDefault(CommunicationChanel.PUSH_NOTIFICATION)).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(CommunicationChanel.favoriteOrDefault(null)).isEqualTo(CommunicationChanel.EMAIL);
    }
}
