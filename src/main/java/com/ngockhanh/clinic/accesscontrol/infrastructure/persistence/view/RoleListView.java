package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.util.UUID;

/**
 * Row of {@code roles} with its usage counts for role administration. Component order matches the
 * SELECT column order used for constructor mapping.
 */
public record RoleListView(
    UUID id,
    String code,
    String name,
    String description,
    Boolean active,
    Long rowVersion,
    Long permissionCount,
    Long userCount) {}
