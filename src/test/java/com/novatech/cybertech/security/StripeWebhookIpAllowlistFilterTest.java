package com.novatech.cybertech.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StripeWebhookIpAllowlistFilterTest {

    private StripeWebhookIpAllowlistFilter filter;

    @BeforeEach
    void setUp() {
        filter = new StripeWebhookIpAllowlistFilter();
    }

    @Test
    @DisplayName("Disabled flag passes every request through unchanged")
    void shouldPassThroughWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/webhooks/stripe");
        req.setRemoteAddr("203.0.113.7"); // not on the allowlist
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Non-stripe path passes through even when enabled")
    void shouldPassThroughOnUnrelatedPath() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/services/order/place");
        req.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Allowlisted IP on stripe path is forwarded to the chain")
    void shouldPassThroughForAllowlistedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/webhooks/stripe");
        req.setRemoteAddr("3.18.12.63"); // first IP in the snapshot
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Non-allowlisted IP on stripe path returns 403 with json body")
    void shouldReturn403ForUntrustedIp() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/webhooks/stripe");
        req.setRemoteAddr("203.0.113.7"); // RFC 5737 documentation range, never an allowlisted IP
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(res.getContentAsString()).contains("forbidden").contains("untrusted_source");
    }

    @Test
    @DisplayName("Null remote address on stripe path returns 403")
    void shouldReturn403WhenRemoteAddrNull() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/webhooks/stripe");
        req.setRemoteAddr(null);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, never()).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Null request URI on enabled filter passes through (no path match)")
    void shouldPassThroughOnNullUri() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", null);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain, times(1)).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
