package com.ngockhanh.clinic.healthexamination.application.command;

import lombok.Builder;

/**
 * Raw list input. Every field is nullable; the use case applies defaults and validates bounds.
 *
 * @param page one-based page number
 * @param size page size
 * @param searchKey optional text matched against tax code and name
 * @param sortKey one of {@code id}, {@code taxCode}, {@code name}
 * @param sortBy {@code ASC} or {@code DESC}
 */
@Builder
public record ListOrganizationCommand(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
