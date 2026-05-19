package querydsl.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Regression tests for Phase 3 fix 3.2 — per-IP rate limit on {@code POST /auth/login}.
 */
class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setup() {
        filter = new LoginRateLimitFilter(new ObjectMapper());
        // 3 requests / minute, makes the test fast and obvious.
        ReflectionTestUtils.setField(filter, "capacity", 3L);
        ReflectionTestUtils.setField(filter, "refillPeriodSeconds", 60L);
        chain = mock(FilterChain.class);
    }

    @Test
    void withinLimit_passesThrough() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(loginRequest("10.0.0.1"), response, chain);
            assertEquals(200, response.getStatus(), "request " + (i + 1) + " should pass");
        }
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void overLimit_returns429WithRetryAfter() throws Exception {
        // Burn through the bucket.
        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(loginRequest("10.0.0.2"), new MockHttpServletResponse(), chain);
        }
        // Now the 4th in the same minute must trip the limit.
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("10.0.0.2"), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void differentIps_haveSeparateBuckets() throws Exception {
        // IP A burns its allowance.
        for (int i = 0; i < 3; i++) {
            filter.doFilterInternal(loginRequest("10.0.0.3"), new MockHttpServletResponse(), chain);
        }
        // IP B is unaffected.
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("10.0.0.4"), ok, chain);
        assertEquals(200, ok.getStatus());
    }

    @Test
    void nonLoginRequest_isNotRateLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/refresh");
            req.setRemoteAddr("10.0.0.5");
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(50)).doFilter(any(), any());
    }

    @Test
    void getRequestToLogin_isNotRateLimited() throws Exception {
        // The limit only applies to POST. GETs (404 from the controller side anyway) are skipped.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/auth/login");
        req.setRemoteAddr("10.0.0.6");
        filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        verify(chain, never()).doFilter(null, null);
    }

    @Test
    void forwardedForHeader_isUsedAsBucketKey() throws Exception {
        // Same remoteAddr, different X-Forwarded-For → different buckets.
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = loginRequest("10.0.0.10");
            req.addHeader("X-Forwarded-For", "203.0.113.7");
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest sameProxyDifferentClient = loginRequest("10.0.0.10");
        sameProxyDifferentClient.addHeader("X-Forwarded-For", "203.0.113.8");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilterInternal(sameProxyDifferentClient, ok, chain);
        assertEquals(200, ok.getStatus(), "Different client behind same proxy must not share a bucket");
    }

    private static MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }
}
