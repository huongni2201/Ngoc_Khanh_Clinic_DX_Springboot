package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.identity.infrastructure.security.ServerJwtTokens;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JwtSessionValidationTest {
  final Instant now = Instant.parse("2026-09-28T00:00:00Z");
  final byte[] key = new byte[64];
  final ServerJwtTokens codec =
      new ServerJwtTokens(
          SessionAdaptersTest.jwtSettings(Base64.getEncoder().encodeToString(key)),
          SessionAdaptersTest.settings(),
          Clock.fixed(now, ZoneOffset.UTC));
  final UUID user = UUID.randomUUID();

  JWTClaimsSet.Builder valid() {
    return new JWTClaimsSet.Builder()
        .subject(user.toString())
        .issuer("nkc")
        .audience("nkc-staff")
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(28800)))
        .jwtID(UUID.randomUUID().toString())
        .claim("userId", user.toString())
        .claim("staffId", UUID.randomUUID().toString())
        .claim("username", "staff")
        .claim("principalType", "STAFF")
        .claim(
            "roleAssignments",
            List.of(
                Map.of(
                    "roleId",
                    UUID.randomUUID().toString(),
                    "roleCode",
                    "DOCTOR",
                    "permissions",
                    List.of("READ"),
                    "grantedBy",
                    user.toString(),
                    "grantedAt",
                    now.toString())));
  }

  String sign(JWTClaimsSet.Builder claims, JWSAlgorithm algorithm) throws Exception {
    var token = new SignedJWT(new JWSHeader(algorithm), claims.build());
    token.sign(new MACSigner(key));
    return token.serialize();
  }

  @Test
  void everyRequiredClaimIsMandatory() throws Exception {
    for (String claim :
        List.of(
            "sub",
            "iss",
            "aud",
            "iat",
            "exp",
            "jti",
            "userId",
            "staffId",
            "username",
            "principalType",
            "roleAssignments")) {
      String token = sign(valid().claim(claim, null), JWSAlgorithm.HS256);
      assertThat(codec.verify(token)).as("Missing %s", claim).isEmpty();
    }
  }

  @Test
  void algorithmIssuerAudienceSubjectAndLifetimeMustMatch() throws Exception {
    List<String> invalid =
        List.of(
            sign(valid(), JWSAlgorithm.HS384),
            sign(valid().issuer("other"), JWSAlgorithm.HS256),
            sign(valid().audience("other"), JWSAlgorithm.HS256),
            sign(valid().subject(UUID.randomUUID().toString()), JWSAlgorithm.HS256),
            sign(valid().claim("principalType", "PATIENT"), JWSAlgorithm.HS256),
            sign(valid().issueTime(Date.from(now.plusSeconds(1))), JWSAlgorithm.HS256),
            sign(valid().expirationTime(Date.from(now.plusSeconds(28801))), JWSAlgorithm.HS256),
            sign(valid().expirationTime(Date.from(now)), JWSAlgorithm.HS256));
    for (String token : invalid) {
      assertThat(codec.verify(token)).isEmpty();
    }
    assertThat(codec.verify(sign(valid(), JWSAlgorithm.HS256)).orElseThrow().userId())
        .isEqualTo(user);
  }

  @Test
  void acceptsAccountRoleAndRolelessStaffAndPatientTokens() throws Exception {
    var staff =
        codec
            .verify(sign(valid().claim("roleAssignments", List.of()), JWSAlgorithm.HS256))
            .orElseThrow();
    assertThat(staff.principalType()).isEqualTo("STAFF");
    assertThat(staff.patientId()).isNull();
    assertThat(staff.roles()).isEmpty();
    UUID patientId = UUID.randomUUID();
    var patient =
        codec
            .verify(
                sign(
                    valid()
                        .claim("staffId", null)
                        .claim("patientId", patientId.toString())
                        .claim("principalType", "PATIENT")
                        .claim("roleAssignments", List.of()),
                    JWSAlgorithm.HS256))
            .orElseThrow();
    assertThat(patient.patientId()).isEqualTo(patientId);
    assertThat(patient.staffId()).isNull();
    assertThat(patient.principalType()).isEqualTo("PATIENT");
    assertThat(patient.roles()).isEmpty();
    assertThat(codec.verify(codec.issue(patient))).contains(patient);
    assertThat(codec.verify(codec.issue(staff))).contains(staff);
  }

  @Test
  void rejectsMissingOrContradictoryIdentityLinksAndUnsupportedTypes() throws Exception {
    for (var claims :
        List.of(
            valid().claim("patientId", UUID.randomUUID().toString()),
            valid().claim("principalType", "PATIENT").claim("staffId", null),
            valid()
                .claim("principalType", "PATIENT")
                .claim("patientId", UUID.randomUUID().toString()),
            valid().claim("principalType", "ADMIN"),
            valid().claim("patientId", "not-a-uuid"))) {
      assertThat(codec.verify(sign(claims, JWSAlgorithm.HS256))).isEmpty();
    }
  }
}
