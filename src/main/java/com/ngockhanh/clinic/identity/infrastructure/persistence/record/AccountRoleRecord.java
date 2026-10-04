package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record AccountRoleRecord(UUID accountId, UUID roleId, UUID grantedBy, Instant grantedAt) {}
