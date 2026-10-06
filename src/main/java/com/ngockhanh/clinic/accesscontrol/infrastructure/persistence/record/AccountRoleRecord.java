package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
/** Persistence row of {@code public.account_roles}. */
public record AccountRoleRecord(UUID accountId, UUID roleId, UUID grantedBy, Instant grantedAt) {}
