package com.novatech.cybertech.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final String PATH = "path";
    private static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        MDC.put(REQUEST_ID, UUID.randomUUID().toString());
        MDC.put(PATH, request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // TRÈS IMPORTANT
        }
    }
}
