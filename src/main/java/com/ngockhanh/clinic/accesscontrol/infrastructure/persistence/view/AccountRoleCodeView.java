package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.util.UUID;

/** Active role code granted to an account; component order matches the SELECT column order. */
public record AccountRoleCodeView(UUID accountId, String roleCode) {}
