package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.port.ParticipantListReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
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
 * Lists the Participants of one health examination batch with filters, allowlisted sorting and
 * one-based pagination.
 *
 * <p>The caller must hold the Participant read permission, checked before the batch is looked up so
 * the endpoint reveals nothing to callers without it. The batch is read by organization and batch
 * together; a deleted batch or one of another organization is reported as not found, while an
 * inactive organization or a batch in any lifecycle state can still be read. Roster rows of every
 * status are listed unless a filter says otherwise. The count and the page are read in one
 * repeatable-read transaction so they agree with each other. Nothing is written or audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListParticipantsUseCase {
  static final int MAX_SEARCH_KEY_LENGTH = 200;
  private static final Set<String> SORT_KEYS =
      Set.of("id", "fullName", "participantCode", "examinationDate", "createdAt");
  private static final Set<String> ROSTER_STATUSES = Set.of("ACTIVE", "CANCELLED");
  private static final Set<String> ATTENDANCE_STATUSES = Set.of("UNCONFIRMED", "ATTENDED", "ABSENT");
  private static final Set<String> RECONCILIATION_STATUSES = Set.of("PENDING", "RECONCILED");

  private final ParticipantAccessPolicy access;
  private final ParticipantListReader reader;

  /**
   * Returns one page of Participants.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param query raw list input; {@code null} fields fall back to defaults
   * @param principal authenticated staff account
   * @return the requested page; items are empty when the page is past the last page
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the read permission
   * @throws IllegalArgumentException when an argument is null or a value is out of bounds
   * @throws ResourceNotFoundException when the batch is not found in the organization
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<ParticipantSummaryResponse> execute(
      UUID organizationId, UUID batchId, ParticipantListQuery query, UserPrincipal principal) {
    access.requireRead(principal);
    if (organizationId == null || batchId == null || query == null)
      throw new IllegalArgumentException("Organization ID, batch ID and list query are required");
    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");

    String searchKey = query.searchKey() == null ? "" : query.searchKey().trim();
    if (searchKey.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");
    String sortKey = blankToDefault(query.sortKey(), PaginationConstants.DEFAULT_SORTED_KEY);
    if (!SORT_KEYS.contains(sortKey)) throw new IllegalArgumentException("Sort key is not allowed");
    String sortBy =
        blankToDefault(query.sortBy(), PaginationConstants.DEFAULT_SORTED_BY).toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC"))
      throw new IllegalArgumentException("Sort direction must be ASC or DESC");
    String identification = blankToNull(query.identificationNumber());
    if (identification != null) IdentificationNumber.of(identification);

    ParticipantListCriteria criteria =
        ParticipantListCriteria.builder()
            .offset((long) (page - 1) * size)
            .limit(size)
            .searchPattern(likePattern(searchKey))
            .identificationNumber(identification)
            .batchDayId(query.batchDayId())
            .rosterStatus(allowed(query.rosterStatus(), ROSTER_STATUSES))
            .attendanceStatus(allowed(query.attendanceStatus(), ATTENDANCE_STATUSES))
            .reconciliationStatus(allowed(query.reconciliationStatus(), RECONCILIATION_STATUSES))
            .sortKey(sortKey)
            .sortBy(sortBy)
            .build();
    var result =
        reader
            .readPage(organizationId, batchId, criteria)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    List<ParticipantSummaryResponse> items =
        result.items().stream().map(ParticipantSummaryResponse::from).toList();
    log.debug(
        "Participants listed: organizationId={}, batchId={}, page={}, size={}, returned={}, total={}",
        organizationId,
        batchId,
        page,
        size,
        items.size(),
        result.totalElements());
    long total = result.totalElements();
    return PageResponse.<ParticipantSummaryResponse>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  private static String allowed(String value, Set<String> allowlist) {
    String candidate = blankToNull(value);
    if (candidate == null) return null;
    if (!allowlist.contains(candidate)) throw new IllegalArgumentException("Filter value is not allowed");
    return candidate;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  /** Builds a lower-case contains pattern in which user-typed LIKE wildcards match literally. */
  private static String likePattern(String searchKey) {
    if (searchKey.isEmpty()) return null;
    String escaped =
        searchKey.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
