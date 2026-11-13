package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.dto.response.gorse.GorseScoredItemDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client for Gorse's recommendation engine REST API
 * (https://gorse.io/docs/api/restful-api.html). Two call profiles live here:
 *
 * <ul>
 *   <li><b>Writes</b> ({@code upsertItems}/{@code upsertUsers}/{@code upsertFeedback}) — called
 *   only from {@code GorseSyncTasklet} (scheduled job, not on any user request path). A failed
 *   call here simply means this run's data doesn't land; the next scheduled run covers it via
 *   its lookback window. {@code @Retry} alone (no {@code @CircuitBreaker}, no fallback): retries
 *   absorb a transient blip, and anything past that is left to propagate so the tasklet's
 *   per-phase try/catch can log it and move on instead of duplicating "log and swallow" here.</li>
 *   <li><b>Reads</b> ({@code getRecommendations}/{@code getLatestItems}) — called from
 *   {@code RecommendationServiceImp} on the home-page request path, so they get
 *   {@code @CircuitBreaker} too (same rationale as {@link CommentModerationClient}: fail fast on
 *   a sustained Gorse outage instead of making every page load wait out retries). Still no
 *   fallback method — {@code RecommendationServiceImp} owns the recommend → latest → best-sellers
 *   cascade, not this client.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GorseClient {

    // Official Gorse RESTful API paths (https://gorse.io/docs/api/restful-api.html) — batch
    // insert endpoints for items/users/feedback, verified against the published docs.
    private static final String ITEMS_PATH = "/api/items";
    private static final String USERS_PATH = "/api/users";
    private static final String FEEDBACK_PATH = "/api/feedback";
    private static final String RECOMMEND_PATH = "/api/recommend/";
    private static final String LATEST_PATH = "/api/latest";

    private final RestClient restClient;

    @Value("${gorse.api.url}")
    private String gorseApiUrl;

    @Retry(name = "gorseSync")
    public void upsertItems(final List<GorseItemDto> items) {
        restClient.post()
                .uri(gorseApiUrl + ITEMS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(items)
                .retrieve()
                .toBodilessEntity();
        log.info("Synced {} item(s) to Gorse", items.size());
    }

    @Retry(name = "gorseSync")
    public void upsertUsers(final List<GorseUserDto> users) {
        restClient.post()
                .uri(gorseApiUrl + USERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(users)
                .retrieve()
                .toBodilessEntity();
        log.info("Synced {} user(s) to Gorse", users.size());
    }

    /**
     * Uses {@code PUT}, not {@code POST}: per the official docs, {@code POST /api/feedback}
     * <i>accumulates</i> the {@code Value} field when the same (UserId, ItemId, FeedbackType)
     * triplet is submitted again, while {@code PUT} overwrites it. Our sliding lookback window
     * (see {@code GorseSyncTasklet}) deliberately re-sends the overlap between two runs, so we
     * need the overwrite semantics — {@code POST} here would double-count every event caught in
     * that overlap.
     */
    @Retry(name = "gorseSync")
    public void upsertFeedback(final List<GorseFeedbackDto> feedback) {
        restClient.put()
                .uri(gorseApiUrl + FEEDBACK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(feedback)
                .retrieve()
                .toBodilessEntity();
        log.info("Synced {} feedback event(s) to Gorse", feedback.size());
    }

    /**
     * {@code GET /api/recommend/{user-id}} — Gorse's precomputed personalized recommendations,
     * most relevant first. Returns a plain list of item ids (product UUIDs as strings); empty
     * for a cold-start user with no feedback history yet, not an error.
     */
    @Retry(name = "gorseRead")
    @CircuitBreaker(name = "gorseRead")
    public List<String> getRecommendations(final String userId, final int n) {
        return restClient.get()
                .uri(gorseApiUrl + RECOMMEND_PATH + userId + "?n=" + n)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });
    }

    /**
     * {@code GET /api/latest} — newest items by timestamp, no server-side recommender config
     * required. Used as the cold-start fallback before falling further back to
     * {@code ProductRepository#findBestSellers}.
     */
    @Retry(name = "gorseRead")
    @CircuitBreaker(name = "gorseRead")
    public List<GorseScoredItemDto> getLatestItems(final int n) {
        return restClient.get()
                .uri(gorseApiUrl + LATEST_PATH + "?n=" + n)
                .retrieve()
                .body(new ParameterizedTypeReference<List<GorseScoredItemDto>>() {
                });
    }
}
