package com.novatech.cybertech.api.error;

import com.novatech.cybertech.api.error.enumpackage.ErrorCode;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(@NonNull HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {

        log.warn("✖ 403 access denied on {} {}: {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        final ErrorResponseDto error = ErrorResponseDto.of("Access denied", ErrorCode.ACCESS_DENIED);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(APPLICATION_JSON_VALUE);
        response.getWriter().write(new ObjectMapper().writeValueAsString(error));
    }
}