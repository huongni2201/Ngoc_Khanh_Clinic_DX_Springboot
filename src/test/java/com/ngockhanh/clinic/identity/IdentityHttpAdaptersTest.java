package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.identity.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.identity.api.http.TrustedProxyClientIpResolver;
import com.ngockhanh.clinic.identity.api.request.LoginRequest;
import com.ngockhanh.clinic.identity.application.command.LoginCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutSessionCommand;
import com.ngockhanh.clinic.identity.application.query.AuthenticateSessionQuery;
import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import com.ngockhanh.clinic.identity.application.response.LoginResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentityHttpAdaptersTest {
  @Test
  void loginBuilderPreservesDefensiveSessionCopy() {
    List<String> sessionIds = new ArrayList<>(List.of("session-secret"));
    var command = LoginCommand.builder().sessionIds(sessionIds).build();
    sessionIds.clear();
    assertThat(command.sessionIds()).containsExactly("session-secret");
    assertThatThrownBy(() -> command.sessionIds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void authenticationBuildersDoNotExposeCredentialsOrTokens() {
    String secret = "sensitive-test-secret";
    List<Object> builders =
        List.of(
            LoginRequest.builder().username(secret).password(secret),
            LoginCommand.builder().username(secret).password(secret).sessionIds(List.of(secret)),
            LogoutSessionCommand.builder().sessionIds(List.of(secret)),
            AuthenticateSessionQuery.builder().sessionIds(List.of(secret)),
            GetCsrfTokenQuery.builder().token(secret),
            CsrfResponse.builder().token(secret),
            LoginResult.builder().sessionId(secret));
    assertThat(builders)
        .allSatisfy(builder -> assertThat(builder.toString()).doesNotContain(secret));
  }

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
