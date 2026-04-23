package com.novatech.cybertech.api.error;

import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

/**
 * Unit tests for {@link CustomAccessDeniedHandler}.
 *
 * Covers happy-path body shape and ensures the handler does not leak the
 * internal exception message back to the caller (security hardening).
 */
class CustomAccessDeniedHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();

    @Test
    @DisplayName("handle writes a 403 ErrorResponseDto JSON body")
    void handleWritesForbiddenJsonBody() throws IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/secured");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(APPLICATION_JSON_VALUE);

        final ErrorResponseDto body = MAPPER.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertThat(body.getMessage()).isEqualTo("Access denied");
        assertThat(body.getHttpStatusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(body.getErrorCodeType()).isEqualTo(ErrorCodeType.FUNCTIONAL);
    }

    @Test
    @DisplayName("handle does not leak the underlying exception message in the body")
    void handleDoesNotLeakExceptionMessage() throws IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/admin/wipe");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final String sensitive = "internal-secret-XYZ-7f3a2c";

        handler.handle(request, response, new AccessDeniedException(sensitive));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).doesNotContain(sensitive);

        final ErrorResponseDto body = MAPPER.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertThat(body.getMessage()).doesNotContain(sensitive);
    }
}
