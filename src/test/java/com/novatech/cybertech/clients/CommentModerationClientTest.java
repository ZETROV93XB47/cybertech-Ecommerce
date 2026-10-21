package com.novatech.cybertech.clients;

import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.exceptions.ModerationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link CommentModerationClient} against a {@link MockRestServiceServer}: the
 * configured URL is called as is, a verdict is passed through, and a non-2xx status or a timeout
 * is thrown (never turned into a verdict) so that the Resilience4j fallback
 * {@link CommentModerationClient#moderationUnavailable(String, Throwable)} reports an
 * unavailability. Retries and the fallback wiring themselves are AOP advice, covered by
 * {@code ReviewModerationFlowIT}.
 */
@DisplayName("CommentModerationClient — HTTP call to the moderation service")
class CommentModerationClientTest {

    private static final String MODERATION_API_URL = "http://moderation-api:5000/api/v1/moderation/analyze";

    private MockRestServiceServer server;
    private CommentModerationClient client;

    @BeforeEach
    void setUp() {
        final RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CommentModerationClient(builder.build(), new ObjectMapper());
        ReflectionTestUtils.setField(client, "moderationApiUrl", MODERATION_API_URL);
    }

    @Test
    @DisplayName("POSTs {comment} to the configured /api/v1/moderation/analyze URL and returns a clean verdict")
    void cleanVerdict() {
        server.expect(requestTo(MODERATION_API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"comment\":\"Très bon produit\"}"))
                .andRespond(withSuccess("{\"label\":\"non-toxic\",\"score\":0.98,\"isHateful\":false}", MediaType.APPLICATION_JSON));

        final ModerationResponseDto verdict = client.moderate("Très bon produit");

        assertThat(verdict.getIsHateful()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("isHateful=true is returned as is: the decision belongs to the review service")
    void hatefulVerdict() {
        server.expect(requestTo(MODERATION_API_URL)).andRespond(withSuccess(
                "{\"label\":\"toxic\",\"score\":0.99,\"isHateful\":true}", MediaType.APPLICATION_JSON));

        final ModerationResponseDto verdict = client.moderate("Très bon produit");

        assertThat(verdict.getIsHateful()).isTrue();
        assertThat(verdict.getScore()).isEqualTo(0.99);
        server.verify();
    }

    @Test
    @DisplayName("404 (wrong path) is thrown, not read as a verdict")
    void notFoundIsThrown() {
        server.expect(requestTo(MODERATION_API_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.moderate("Très bon produit")).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    @DisplayName("5xx is thrown, not read as a verdict")
    void serverErrorIsThrown() {
        server.expect(requestTo(MODERATION_API_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.moderate("Très bon produit")).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("a read timeout is thrown as ResourceAccessException")
    void timeoutIsThrown() {
        server.expect(requestTo(MODERATION_API_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.moderate("Très bon produit")).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    @DisplayName("fallback: ModerationUnavailableException (503), never CommentPostNotAllowedException, cause kept")
    void fallbackReportsUnavailability() {
        final HttpClientErrorException cause = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        assertThatThrownBy(() -> client.moderationUnavailable("Très bon produit", cause))
                .isInstanceOf(ModerationUnavailableException.class)
                .hasCause(cause);
    }
}
