package com.novatech.cybertech.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Non-matching path passes through without consuming a bucket")
    void shouldPassThroughForUnmatchedPath() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/services/product/list");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(filter.bucketsView()).isEmpty();
    }

    @Test
    @DisplayName("Register POST consumes the bucket and passes through under the limit")
    void shouldPassThroughRegisterUnderLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/user/register");
            req.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, res, chain);

            verify(chain, times(1)).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        assertThat(filter.bucketsView()).containsKey("register:ip:10.0.0.1");
    }

    @Test
    @DisplayName("6th register call from same IP returns 429 with Retry-After")
    void shouldReturn429WhenRegisterBucketExhausted() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/user/register");
            req.setRemoteAddr("10.0.0.2");
            filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest sixth = new MockHttpServletRequest("POST", "/api/v1/services/user/register");
        sixth.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(sixth, res, chain);

        verify(chain, never()).doFilter(sixth, res);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(res.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1L);
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(res.getContentAsString()).contains("too_many_requests").contains("retryAfterSeconds");
    }

    @Test
    @DisplayName("/register/auto/* (admin path) does NOT consume the human-signup bucket")
    void shouldNotMatchRegisterAutoExactPathOnly() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/user/register/auto");
        req.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(filter.bucketsView()).isEmpty();
    }

    @Test
    @DisplayName("Order place path matches POST only — GET passes through")
    void shouldMatchOrderPlaceOnPostOnly() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/services/management/order/place");
        get.setRemoteAddr("10.0.0.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(get, res, chain);

        verify(chain, times(1)).doFilter(get, res);
        assertThat(filter.bucketsView()).isEmpty();
    }

    @Test
    @DisplayName("Cart path matches any HTTP method")
    void shouldMatchCartPathAnyMethod() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/services/cart/list");
        get.setRemoteAddr("10.0.0.5");

        filter.doFilter(get, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(filter.bucketsView()).containsKey("cart:ip:10.0.0.5");
    }

    @Test
    @DisplayName("Authenticated request uses JWT subject as bucket client id")
    void shouldUseJwtSubjectAsClientId() throws Exception {
        Jwt jwt = Jwt.withTokenValue("fake")
                .header("alg", "none")
                .claim("sub", "user-uuid-42")
                .subject("user-uuid-42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var authentication = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/cart/add");
        req.setRemoteAddr("10.0.0.6");

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(filter.bucketsView()).containsKey("cart:sub:user-uuid-42");
        assertThat(filter.bucketsView()).doesNotContainKey("cart:ip:10.0.0.6");
    }

    @Test
    @DisplayName("Null remote address falls back to ip:unknown")
    void shouldFallBackWhenRemoteAddrNull() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/user/register");
        // MockHttpServletRequest defaults remoteAddr to "127.0.0.1"; force null via setter
        req.setRemoteAddr(null);

        filter.doFilter(req, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(filter.bucketsView()).containsKey("register:ip:unknown");
    }

    @Test
    @DisplayName("Null request URI passes through")
    void shouldPassThroughOnNullUri() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", null);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(filter.bucketsView()).isEmpty();
    }
}
