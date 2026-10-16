package com.ngockhanh.clinic.accesscontrol.application.query;

import lombok.Builder;

/**
 * Raw list input. Every field is nullable; the use case applies defaults and validates bounds.
 *
 * @param page one-based page number
 * @param size page size
 */
@Builder
public record ListRolesQuery(Integer page, Integer size) {}
