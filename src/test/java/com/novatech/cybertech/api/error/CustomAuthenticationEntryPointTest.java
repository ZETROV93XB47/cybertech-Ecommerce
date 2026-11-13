package com.novatech.cybertech.api.error;

import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

/**
 * Unit tests for {@link CustomAuthenticationEntryPoint}.
 */
class CustomAuthenticationEntryPointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();

    @Test
    @DisplayName("commence writes a 401 ErrorResponseDto JSON body")
    void commenceWritesUnauthorizedJsonBody() throws IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/secured");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(APPLICATION_JSON_VALUE);

        final ErrorResponseDto body = MAPPER.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertThat(body.getMessage()).isEqualTo("Authentication required");
        assertThat(body.getHttpStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(body.getErrorCodeType()).isEqualTo(ErrorCodeType.TECHNICAL);
    }

    @Test
    @DisplayName("commence does not leak the underlying exception message in the body")
    void commenceDoesNotLeakExceptionMessage() throws IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/secured");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final String sensitive = "JWT-leak-DEADBEEF-token";
        final AuthenticationException ex = new BadCredentialsException(sensitive);

        entryPoint.commence(request, response, ex);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).doesNotContain(sensitive);

        final ErrorResponseDto body = MAPPER.readValue(response.getContentAsString(), ErrorResponseDto.class);
        assertThat(body.getMessage()).doesNotContain(sensitive);
    }
}
