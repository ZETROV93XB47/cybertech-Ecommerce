package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.clients.CommentModerationClient;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.services.implementation.ModerationServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link ModerationServiceImp}.
 *
 * <p>SA-W3.5 wave — services/catalog. Pins BUG-086 (no caching, every call hits the
 * sidecar HTTP client). Service is a single-method passthrough so coverage is intentionally
 * thin but documents the fail-closed contract on client errors.
 */
@ExtendWith(MockitoExtension.class)
class ModerationServiceImpTest {

    @Mock CommentModerationClient client;

    @InjectMocks ModerationServiceImp service;

    @Test
    @DisplayName("delegates to CommentModerationClient.moderate and returns the response unchanged")
    void checkIfIsHateful_happyPath() {
        ModerationResponseDto expected = ModerationResponseDto.builder().label("not_hate").score(0.01).build();
        when(client.moderate("hello there")).thenReturn(expected);

        ModerationResponseDto result = service.checkIfIsHateful("hello there");

        assertThat(result).isSameAs(expected);
        verify(client).moderate("hello there");
    }

    @Test
    @DisplayName("client RuntimeException is propagated (fail-closed)")
    void checkIfIsHateful_clientThrows_propagates() {
        when(client.moderate("bad")).thenThrow(new RuntimeException("sidecar 500"));

        assertThatThrownBy(() -> service.checkIfIsHateful("bad"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("sidecar 500");
    }

    @Test
    @DisplayName("BUG-086: no caching — two identical calls invoke the client twice")
    void bug086_noCaching_twoCallsTwoInvocations() {
        ModerationResponseDto resp = ModerationResponseDto.builder().label("hate").score(0.91).build();
        when(client.moderate("repeat me")).thenReturn(resp);

        service.checkIfIsHateful("repeat me");
        service.checkIfIsHateful("repeat me");

        verify(client, times(2)).moderate("repeat me");
    }

    @Test
    @DisplayName("BUG-086: distinct comments each round-trip")
    void bug086_distinctCommentsRoundTrip() {
        when(client.moderate("a")).thenReturn(ModerationResponseDto.builder().label("ok").score(0.0).build());
        when(client.moderate("b")).thenReturn(ModerationResponseDto.builder().label("ok").score(0.0).build());

        service.checkIfIsHateful("a");
        service.checkIfIsHateful("b");
        service.checkIfIsHateful("a");

        verify(client, times(2)).moderate("a");
        verify(client, times(1)).moderate("b");
    }

    @Test
    @DisplayName("null comment passes straight through to the client (no defensive guard)")
    void checkIfIsHateful_nullComment_passthrough() {
        when(client.moderate(null)).thenReturn(ModerationResponseDto.builder().label("ok").score(0.0).build());

        ModerationResponseDto result = service.checkIfIsHateful(null);

        assertThat(result).isNotNull();
        verify(client).moderate(null);
    }

    @Test
    @DisplayName("client returns null — service returns null (no NPE-guard)")
    void checkIfIsHateful_clientReturnsNull_returnsNull() {
        when(client.moderate("x")).thenReturn(null);

        assertThat(service.checkIfIsHateful("x")).isNull();
    }
}
