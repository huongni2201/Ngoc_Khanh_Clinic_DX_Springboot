package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.application.port.AuthenticationFailure;
import com.ngockhanh.clinic.identity.infrastructure.security.ServerJwtTokens;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtSessionValidationTest {
    final Instant now = Instant.parse("2026-09-28T00:00:00Z");
    final byte[] key = new byte[64];
    final ServerJwtTokens codec = new ServerJwtTokens(Base64.getEncoder().encodeToString(key),
            SessionAdaptersTest.settings(), Clock.fixed(now, ZoneOffset.UTC));
    final UUID user = UUID.randomUUID();

    JWTClaimsSet.Builder valid() {
        return new JWTClaimsSet.Builder().subject(user.toString()).issuer("nkc").audience("nkc-staff")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(28800))).jwtID(UUID.randomUUID().toString())
                .claim("userId", user.toString()).claim("staffId", UUID.randomUUID().toString())
                .claim("username", "staff").claim("principalType", "STAFF")
                .claim("roleAssignments", List.of(Map.of("assignmentId", UUID.randomUUID().toString(),
                        "roleCode", "DOCTOR", "permissions", List.of("READ"), "validFrom", now.toString())));
    }
    String sign(JWTClaimsSet.Builder claims, JWSAlgorithm algorithm) throws Exception {
        var token = new SignedJWT(new JWSHeader(algorithm), claims.build());
        token.sign(new MACSigner(key));
        return token.serialize();
    }

    @Test void everyRequiredClaimIsMandatory() throws Exception {
        for (String claim : List.of("sub", "iss", "aud", "iat", "exp", "jti",
                "userId", "staffId", "username", "principalType", "roleAssignments")) {
            String token = sign(valid().claim(claim, null), JWSAlgorithm.HS256);
            assertThatThrownBy(() -> codec.verify(token)).as("Missing %s", claim).isInstanceOf(AuthenticationFailure.class);
        }
    }

    @Test void algorithmIssuerAudienceSubjectAndLifetimeMustMatch() throws Exception {
        List<String> invalid = List.of(
                sign(valid(), JWSAlgorithm.HS384),
                sign(valid().issuer("other"), JWSAlgorithm.HS256),
                sign(valid().audience("other"), JWSAlgorithm.HS256),
                sign(valid().subject(UUID.randomUUID().toString()), JWSAlgorithm.HS256),
                sign(valid().claim("principalType", "PATIENT"), JWSAlgorithm.HS256),
                sign(valid().issueTime(Date.from(now.plusSeconds(1))), JWSAlgorithm.HS256),
                sign(valid().expirationTime(Date.from(now.plusSeconds(28801))), JWSAlgorithm.HS256),
                sign(valid().expirationTime(Date.from(now)), JWSAlgorithm.HS256),
                sign(valid().claim("roleAssignments", List.of()), JWSAlgorithm.HS256));
        for (String token : invalid) {
            assertThatThrownBy(() -> codec.verify(token)).isInstanceOf(AuthenticationFailure.class);
        }
        assertThat(codec.verify(sign(valid(), JWSAlgorithm.HS256)).userId()).isEqualTo(user);
    }
}
