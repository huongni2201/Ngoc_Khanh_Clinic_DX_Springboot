package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.account_roles}. */
public record AccountRoleRecord(UUID accountId, UUID roleId, UUID grantedBy, Instant grantedAt) {}
