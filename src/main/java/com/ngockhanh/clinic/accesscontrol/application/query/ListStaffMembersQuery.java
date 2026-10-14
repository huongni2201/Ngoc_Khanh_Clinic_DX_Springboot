package com.ngockhanh.clinic.accesscontrol.application.query;

import lombok.Builder;

/**
 * Raw list input. Every field is nullable; the use case applies defaults and validates bounds.
 *
 * @param page one-based page number
 * @param size page size
 * @param searchKey optional text matched against staff code, full name and username
 * @param sortKey one of {@code id}, {@code staffCode}, {@code fullName}
 * @param sortBy {@code ASC} or {@code DESC}
 */
@Builder
public record ListStaffMembersQuery(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
