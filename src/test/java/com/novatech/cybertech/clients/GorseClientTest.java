package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.dto.response.gorse.GorseScoredItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GorseClient}. There's no direct precedent in this codebase (
 * {@code CommentModerationClient} has no dedicated test) — verifies the request shape built for
 * each Gorse endpoint via a deep-stubbed {@link RestClient}. The {@code @Retry} annotation itself
 * is Resilience4j AOP advice and isn't exercised outside a Spring context; here we only pin that
 * an exception from the HTTP call propagates (i.e. there's no swallowing fallback at this layer —
 * that's the tasklet's job, see {@code GorseSyncTasklet}).
 */
@ExtendWith(MockitoExtension.class)
class GorseClientTest {

    private static final String GORSE_API_URL = "http://localhost:8088";

    @Mock(answer = RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private GorseClient gorseClient;

    @BeforeEach
    void setUp() {
        gorseClient = new GorseClient(restClient);
        ReflectionTestUtils.setField(gorseClient, "gorseApiUrl", GORSE_API_URL);
    }

    @Nested
    @DisplayName("Request shape")
    class RequestShape {

        @Test
        @DisplayName("upsertItems POSTs the item list to {baseUrl}/api/items")
        void upsertItems_postsToItemsEndpoint() {
            final List<GorseItemDto> items = List.of(GorseItemDto.builder().itemId("p1").build());
            final RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
            when(restClient.post()).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(GORSE_API_URL + "/api/items")).thenReturn(bodyUriSpec);
            when(bodyUriSpec.contentType(org.springframework.http.MediaType.APPLICATION_JSON)).thenReturn(bodyUriSpec);

            final ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            when(bodyUriSpec.body(bodyCaptor.capture())).thenReturn(bodyUriSpec);

            gorseClient.upsertItems(items);

            assertThat(bodyCaptor.getValue()).isEqualTo(items);
        }

        @Test
        @DisplayName("upsertUsers POSTs the user list to {baseUrl}/api/users")
        void upsertUsers_postsToUsersEndpoint() {
            final List<GorseUserDto> users = List.of(GorseUserDto.builder().userId("u1").build());
            final RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
            when(restClient.post()).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(GORSE_API_URL + "/api/users")).thenReturn(bodyUriSpec);
            when(bodyUriSpec.contentType(org.springframework.http.MediaType.APPLICATION_JSON)).thenReturn(bodyUriSpec);

            final ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            when(bodyUriSpec.body(bodyCaptor.capture())).thenReturn(bodyUriSpec);

            gorseClient.upsertUsers(users);

            assertThat(bodyCaptor.getValue()).isEqualTo(users);
        }

        @Test
        @DisplayName("upsertFeedback PUTs (not POSTs) the feedback list to {baseUrl}/api/feedback — PUT overwrites, POST accumulates")
        void upsertFeedback_putsToFeedbackEndpoint() {
            final List<GorseFeedbackDto> feedback = List.of(GorseFeedbackDto.builder().itemId("p1").userId("u1").build());
            final RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
            when(restClient.put()).thenReturn(bodyUriSpec);
            when(bodyUriSpec.uri(GORSE_API_URL + "/api/feedback")).thenReturn(bodyUriSpec);
            when(bodyUriSpec.contentType(org.springframework.http.MediaType.APPLICATION_JSON)).thenReturn(bodyUriSpec);

            final ArgumentCaptor<Object> bodyCaptor = ArgumentCaptor.forClass(Object.class);
            when(bodyUriSpec.body(bodyCaptor.capture())).thenReturn(bodyUriSpec);

            gorseClient.upsertFeedback(feedback);

            assertThat(bodyCaptor.getValue()).isEqualTo(feedback);
            verify(restClient, never()).post();
        }

        @Test
        @DisplayName("getRecommendations GETs {baseUrl}/api/recommend/{userId}?n={n} and returns the plain id list")
        void getRecommendations_getsFromRecommendEndpoint() {
            final RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class, RETURNS_DEEP_STUBS);
            final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            when(restClient.get()).thenReturn(headersUriSpec);
            when(headersUriSpec.uri(GORSE_API_URL + "/api/recommend/u1?n=10")).thenReturn(headersUriSpec);
            when(headersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of("item-1", "item-2"));

            final List<String> result = gorseClient.getRecommendations("u1", 10);

            assertThat(result).containsExactly("item-1", "item-2");
        }

        @Test
        @DisplayName("getLatestItems GETs {baseUrl}/api/latest?n={n} and returns the scored item list")
        void getLatestItems_getsFromLatestEndpoint() {
            final RestClient.RequestHeadersUriSpec headersUriSpec = mock(RestClient.RequestHeadersUriSpec.class, RETURNS_DEEP_STUBS);
            final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
            final List<GorseScoredItemDto> scoredItems = List.of(new GorseScoredItemDto("item-1", 0.9));
            when(restClient.get()).thenReturn(headersUriSpec);
            when(headersUriSpec.uri(GORSE_API_URL + "/api/latest?n=5")).thenReturn(headersUriSpec);
            when(headersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(scoredItems);

            final List<GorseScoredItemDto> result = gorseClient.getLatestItems(5);

            assertThat(result).isEqualTo(scoredItems);
        }
    }

    @Nested
    @DisplayName("Failure propagation")
    class FailurePropagation {

        @Test
        @DisplayName("an HTTP failure propagates as-is — no swallowing fallback at this layer")
        void httpFailure_propagates() {
            when(restClient.post()).thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> gorseClient.upsertItems(List.of(GorseItemDto.builder().build())))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("connection refused");
        }

        @Test
        @DisplayName("getRecommendations propagates an HTTP failure — RecommendationServiceImp owns the fallback")
        void getRecommendations_httpFailure_propagates() {
            when(restClient.get()).thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> gorseClient.getRecommendations("u1", 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("connection refused");
        }
    }
}
