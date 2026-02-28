package com.novatech.cybertech.api.error;

import com.novatech.cybertech.api.error.enumpackage.ErrorCodeType;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(@NonNull HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        log.error("Authentication required for request {} with message: {}", request, authException.getMessage());

        final ErrorResponseDto error = new ErrorResponseDto(
                "Authentication required",
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCodeType.TECHNICAL
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(APPLICATION_JSON_VALUE);
        response.getWriter().write(new ObjectMapper().writeValueAsString(error));
    }
}