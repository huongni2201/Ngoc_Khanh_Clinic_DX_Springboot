package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.ngockhanh.clinic.identity.domain.valueobject.SessionPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class ServerJwtTokens implements SessionTokens {
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final JwtSettings jwtSettings;
  private final SessionPolicy sessionPolicy;
  private final Clock clock;

  public ServerJwtTokens(JwtSettings jwtSettings, SessionPolicy sessionPolicy, Clock clock) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(jwtSettings.base64Key());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid JWT key configuration");
    }
    if (key.length < 32)
      throw new IllegalArgumentException("JWT key must contain at least 32 bytes");
    var secret = new SecretKeySpec(key, "HmacSHA256");
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    var verifier = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
    var timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
    verifier.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            timestamps,
            new JwtIssuerValidator(jwtSettings.issuer()),
            jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(jwtSettings.audience())
                    ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                    : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token"))));
    decoder = verifier;
    this.jwtSettings = jwtSettings;
    this.sessionPolicy = sessionPolicy;
    this.clock = clock;
  }

  public String issue(Claims claims) {
    var roles =
        claims.roles().stream()
            .map(
                role -> {
                  Map<String, Object> data = new LinkedHashMap<>();
                  data.put("assignmentId", role.assignmentId().toString());
                  data.put("roleCode", role.roleCode());
                  data.put("permissions", role.permissions());
                  data.put(
                      "departmentId",
                      role.departmentId() == null ? null : role.departmentId().toString());
                  data.put("roomId", role.roomId() == null ? null : role.roomId().toString());
                  data.put("validFrom", role.validFrom().toString());
                  data.put("validTo", role.validTo() == null ? null : role.validTo().toString());
                  return data;
                })
            .toList();
    var payload =
        JwtClaimsSet.builder()
            .issuer(jwtSettings.issuer())
            .audience(List.of(jwtSettings.audience()))
            .subject(claims.userId().toString())
            .id(claims.tokenId().toString())
            .issuedAt(claims.issuedAt())
            .expiresAt(claims.expiresAt())
            .claim("userId", claims.userId().toString())
            .claim("username", claims.username())
            .claim("principalType", claims.principalType())
            .claim("roleAssignments", roles);
    if (claims.staffId() != null) {
      payload.claim("staffId", claims.staffId().toString());
    }
    if (claims.patientId() != null) {
      payload.claim("patientId", claims.patientId().toString());
    }
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), payload.build()))
        .getTokenValue();
  }

  public Optional<Claims> verify(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    Jwt jwt;
    try {
      jwt = decoder.decode(token);
    } catch (JwtException invalidToken) {
      return Optional.empty();
    }
    try {
      Instant now = clock.instant();
      UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
      UUID staffId = uuid(jwt.getClaim("staffId"));
      UUID patientId = uuid(jwt.getClaim("patientId"));
      String principalType = jwt.getClaimAsString("principalType");
      boolean validIdentity =
          switch (principalType) {
            case "STAFF" -> staffId != null && patientId == null;
            case "PATIENT" -> patientId != null && staffId == null;
            case null, default -> false;
          };
      String username = jwt.getClaimAsString("username");
      Instant issued = jwt.getIssuedAt(), expires = jwt.getExpiresAt();
      if (!userId.toString().equals(jwt.getSubject())
          || !validIdentity
          || username == null
          || username.isBlank()
          || username.length() > 200
          || issued == null
          || expires == null
          || issued.isAfter(now)
          || !now.isBefore(expires)
          || !expires.isAfter(issued)
          || Duration.between(issued, expires).compareTo(sessionPolicy.absoluteTimeout()) > 0) {
        return Optional.empty();
      }
      Object raw = jwt.getClaim("roleAssignments");
      if (!(raw instanceof List<?> list)) {
        return Optional.empty();
      }
      List<RoleAssignment> roles = new ArrayList<>();
      for (Object value : list) {
        if (!(value instanceof Map<?, ?> map)
            || !(map.get("permissions") instanceof List<?> permissions)) {
          return Optional.empty();
        }
        roles.add(
            new RoleAssignment(
                UUID.fromString((String) map.get("assignmentId")),
                (String) map.get("roleCode"),
                permissions.stream().map(String.class::cast).toList(),
                uuid(map.get("departmentId")),
                uuid(map.get("roomId")),
                Instant.parse((String) map.get("validFrom")),
                map.get("validTo") == null ? null : Instant.parse((String) map.get("validTo"))));
      }
      return Optional.of(
          new Claims(
              userId,
              staffId,
              patientId,
              username,
              principalType,
              UUID.fromString(jwt.getId()),
              issued,
              expires,
              roles));
    } catch (IllegalArgumentException
        | ClassCastException
        | NullPointerException
        | DateTimeException malformedClaims) {
      return Optional.empty();
    }
  }

  private static UUID uuid(Object value) {
    return value == null ? null : UUID.fromString((String) value);
  }
}
