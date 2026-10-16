package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.query.SessionListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository.SessionCriteria;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the sessions of an organization with filters, allowlisted sorting and one-based pagination.
 *
 * <p>The caller must hold the session view permission, checked first. Each item carries the number
 * of Participants assigned, computed at read time. The count and the page are read in one
 * repeatable-read transaction so they agree with each other. Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListSessionsUseCase {
  static final int MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS =
      Set.of("id", "sessionDate", "activity", "status", "createdAt");

  private final SessionAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationSessionRepository sessions;

  /**
   * Returns one page of sessions.
   *
   * @param organizationId owning organization; it may be inactive
   * @param query raw list input; {@code null} fields fall back to defaults
   * @param principal authenticated staff account
   * @throws ApplicationException {@code ACCESS_DENIED} without the view permission
   * @throws IllegalArgumentException when an argument is null or a value is out of bounds
   * @throws ResourceNotFoundException when the organization does not exist
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<SessionResponse> execute(
      UUID organizationId, SessionListQuery query, UserPrincipal principal) {
    access.require(principal, SessionAccessPolicy.SESSION_VIEW);
    if (organizationId == null || query == null)
      throw new IllegalArgumentException("Organization ID and list query are required");
    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");
    String searchKey = query.searchKey() == null ? "" : query.searchKey().trim();
    if (searchKey.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");
    String sortKey =
        query.sortKey() == null || query.sortKey().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_KEY
            : query.sortKey();
    if (!SORT_KEYS.contains(sortKey)) throw new IllegalArgumentException("Sort key is not allowed");
    String sortBy =
        query.sortBy() == null || query.sortBy().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_BY
            : query.sortBy().trim().toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC"))
      throw new IllegalArgumentException("Sort direction must be ASC or DESC");
    if (query.from() != null && query.to() != null && query.from().isAfter(query.to()))
      throw new IllegalArgumentException("The date range starts after it ends");

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    var criteria =
        new SessionCriteria(
            organizationId,
            query.agreementId(),
            query.from(),
            query.to(),
            blank(query.activity()) ? null : SessionActivity.valueOf(query.activity().trim()),
            blank(query.status()) ? null : SessionStatus.valueOf(query.status().trim()),
            likePattern(searchKey));
    long offset = (long) (page - 1) * size;
    long total = sessions.count(criteria);
    List<SessionResponse> items =
        total == 0 || offset >= total
            ? List.of()
            : sessions.findPage(criteria, offset, size, sortKey, sortBy).stream()
                .map(SessionResponse::from)
                .toList();
    log.info(
        "Sessions listed: organizationId={}, page={}, size={}, returned={}, total={}",
        organizationId,
        page,
        size,
        items.size(),
        total);
    return PageResponse.<SessionResponse>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  /** Builds a lower-case contains pattern in which user-typed LIKE wildcards match literally. */
  private static String likePattern(String searchKey) {
    if (searchKey.isEmpty()) return null;
    String escaped =
        searchKey
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
