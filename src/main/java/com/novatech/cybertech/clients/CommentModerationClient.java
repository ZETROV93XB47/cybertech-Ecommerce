package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentModerationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;


    @Value("${moderation.api.url}")
    private String moderationApiUrl;

    /**
     * Calls the external (Python toxic-bert) moderation service. Wrapped with Resilience4j so a
     * transient blip is retried and a sustained outage trips the circuit breaker quickly instead of
     * hanging every review submission. On exhausted retries / open circuit the call routes to
     * {@link #moderationUnavailable(String, Throwable)} which FAILS CLOSED — we never let an
     * un-moderated comment through when the moderator is unreachable.
     */
    @Retry(name = "moderation")
    @CircuitBreaker(name = "moderation", fallbackMethod = "moderationUnavailable")
    public ModerationResponseDto moderate(final String comment) {
        Map<String, String> body = Map.of("comment", comment);

        ModerationResponseDto response = restClient
                .post()
                .uri(moderationApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ModerationResponseDto.class);

        log.info("Moderation response: {}", response);

        return response;
    }

    /**
     * Fail-closed fallback for {@link #moderate(String)}. Triggered when the moderation service is
     * down (connection refused, timeout, 5xx) after retries, or when the circuit is open. We reject
     * the comment rather than risk publishing unmoderated content.
     */
    @SuppressWarnings("unused") // referenced by name from @CircuitBreaker(fallbackMethod = ...)
    public ModerationResponseDto moderationUnavailable(final String comment, final Throwable t) {
        log.error("Moderation service unavailable — failing closed and rejecting the comment", t);
        throw new CommentPostNotAllowedException(
                "Comment moderation is temporarily unavailable, please try again in a few minutes.");
    }
}
