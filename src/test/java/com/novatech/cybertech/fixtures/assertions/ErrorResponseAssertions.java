package com.novatech.cybertech.fixtures.assertions;

import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.UnsupportedEncodingException;

/**
 * AssertJ-style helper that decodes the canonical {@link ErrorResponseDto} envelope returned by the
 * controller-advice and asserts on its three load-bearing fields. Throws {@link AssertionError} on
 * mismatch — never returns a value.
 */
public final class ErrorResponseAssertions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponseAssertions() {
    }

    public static void assertErrorResponse(final MockHttpServletResponse response,
                                           final int expectedStatus,
                                           final ErrorCodeType expectedType,
                                           final String containsMessage) {
        if (response.getStatus() != expectedStatus) {
            throw new AssertionError("Expected HTTP status " + expectedStatus + " but was " + response.getStatus());
        }

        final ErrorResponseDto body = parseBody(response);

        if (body.getHttpStatusCode() != expectedStatus) {
            throw new AssertionError("Envelope httpStatusCode mismatch: expected " + expectedStatus
                    + " but was " + body.getHttpStatusCode());
        }
        if (body.getErrorCodeType() != expectedType) {
            throw new AssertionError("Envelope errorCodeType mismatch: expected " + expectedType
                    + " but was " + body.getErrorCodeType());
        }
        if (containsMessage != null && (body.getMessage() == null || !body.getMessage().contains(containsMessage))) {
            throw new AssertionError("Envelope message did not contain '" + containsMessage
                    + "', actual: '" + body.getMessage() + "'");
        }
    }

    private static ErrorResponseDto parseBody(final MockHttpServletResponse response) {
        try {
            return MAPPER.readValue(response.getContentAsString(), ErrorResponseDto.class);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("Could not read response body", e);
        }
    }
}
