package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.identity.api.http.TrustedProxyClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityHttpAdaptersTest {
    @Test
    void sessionCookieRetainsSecurityAttributesAndCanBeCleared() {
        var cookie = new SessionCookieFactory(true).create("opaque-id", Duration.ofHours(8));
        assertThat(cookie.getName()).isEqualTo("NKC_SESSION");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(8));

        var cleared = new SessionCookieFactory(false).clear();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.isSecure()).isFalse();
    }

    @Test
    void trustedForwardedChainIsResolvedFromNearestUntrustedAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        var resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.1", "10.0.0.2"));

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void ignoresForwardedHeaderFromUntrustedPeerAndFallsBackOnMalformedChain() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        var resolver = new TrustedProxyClientIpResolver(List.of("10.0.0.2"));
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");

        var malformed = new MockHttpServletRequest();
        malformed.setRemoteAddr("10.0.0.2");
        malformed.addHeader("X-Forwarded-For", "not-an-address");
        assertThat(resolver.resolve(malformed)).isEqualTo("10.0.0.2");
    }
}
