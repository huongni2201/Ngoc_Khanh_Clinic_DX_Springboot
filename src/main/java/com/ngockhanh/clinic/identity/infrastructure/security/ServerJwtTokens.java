package com.ngockhanh.clinic.identity.infrastructure.security;

import com.ngockhanh.clinic.identity.application.usecase.*;
import com.ngockhanh.clinic.identity.application.port.*;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.domain.valueobject.RoleAssignment;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;

public final class ServerJwtTokens implements SessionTokens {
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final AuthSettings settings;
  private final Clock clock;

  public ServerJwtTokens(String base64Key, AuthSettings settings, Clock clock) {
    byte[] key;
    try {
      key = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid JWT key configuration");
    }
    if (key.length < 32) throw new IllegalArgumentException("JWT key must contain at least 32 bytes");
    var secret = new SecretKeySpec(key, "HmacSHA256");
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
    var verifier = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
    var timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
    verifier.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
        timestamps, new JwtIssuerValidator(settings.issuer()),
        jwt -> jwt.getAudience().contains(settings.audience())
            ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
            : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"))));
    decoder = verifier;
    this.settings = settings;
    this.clock = clock;
  }

  public String issue(Claims claims) {
    var roles = claims.roles().stream().map(role -> {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("assignmentId", role.assignmentId().toString());
      data.put("roleCode", role.roleCode());
      data.put("permissions", role.permissions());
      data.put("departmentId", role.departmentId() == null ? null : role.departmentId().toString());
      data.put("roomId", role.roomId() == null ? null : role.roomId().toString());
      data.put("validFrom", role.validFrom().toString());
      data.put("validTo", role.validTo() == null ? null : role.validTo().toString());
      return data;
    }).toList();
    var payload = JwtClaimsSet.builder().issuer(settings.issuer()).audience(List.of(settings.audience()))
        .subject(claims.userId().toString()).id(claims.tokenId().toString())
        .issuedAt(claims.issuedAt()).expiresAt(claims.expiresAt())
        .claim("userId", claims.userId().toString()).claim("staffId", claims.staffId().toString())
        .claim("username", claims.username()).claim("principalType", "STAFF")
        .claim("roleAssignments", roles).build();
    return encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), payload)).getTokenValue();
  }

  public Claims verify(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      Instant now = clock.instant();
      UUID userId = UUID.fromString(jwt.getClaimAsString("userId"));
      UUID staffId = UUID.fromString(jwt.getClaimAsString("staffId"));
      String username = jwt.getClaimAsString("username");
      Instant issued = jwt.getIssuedAt(), expires = jwt.getExpiresAt();
      if (!userId.toString().equals(jwt.getSubject()) || !"STAFF".equals(jwt.getClaimAsString("principalType"))
          || username == null || username.isBlank() || username.length() > 200
          || issued == null || expires == null || issued.isAfter(now) || !now.isBefore(expires)
          || !expires.isAfter(issued) || Duration.between(issued, expires).compareTo(settings.absoluteTimeout()) > 0) {
        throw AuthenticationFailure.invalid();
      }
      Object raw = jwt.getClaim("roleAssignments");
      if (!(raw instanceof List<?> list) || list.isEmpty()) throw AuthenticationFailure.invalid();
      List<RoleAssignment> roles = new ArrayList<>();
      for (Object value : list) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("permissions") instanceof List<?> permissions)) {
          throw AuthenticationFailure.invalid();
        }
        roles.add(new RoleAssignment(UUID.fromString((String) map.get("assignmentId")),
            (String) map.get("roleCode"), permissions.stream().map(String.class::cast).toList(),
            uuid(map.get("departmentId")), uuid(map.get("roomId")),
            Instant.parse((String) map.get("validFrom")),
            map.get("validTo") == null ? null : Instant.parse((String) map.get("validTo"))));
      }
      return new Claims(userId, staffId, username, UUID.fromString(jwt.getId()), issued, expires, roles);
    } catch (RuntimeException e) {
      throw AuthenticationFailure.invalid();
    }
  }

  private static UUID uuid(Object value) {
    return value == null ? null : UUID.fromString((String) value);
  }
}
