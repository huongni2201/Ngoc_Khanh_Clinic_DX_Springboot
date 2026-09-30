package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationBatchParticipantResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantSummary;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListBatchParticipantUseCase {
  private final HealthExaminationBatchRepository batchRepository;
  private final HealthExaminationBatchParticipantRepository batchParticipantRepository;

  @Transactional(readOnly = true)
  public PageResponse<OrganizationBatchParticipantResponse> execute(
      UUID organizationId, UUID batchId, OrganizationBatchParticipantQuery query) {
    if (query == null) throw new IllegalArgumentException("Participant list query is required");

    AggregateId organization = AggregateId.of(organizationId);
    AggregateId batch = AggregateId.of(batchId);

    batchRepository
        .findByIdAndOrganizationId(batch, organization)
        .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();

    String sortKey = normalizeOrDefault(query.sortKey(), PaginationConstants.DEFAULT_SORTED_KEY);

    String sortBy =
        normalizeOrDefault(query.sortBy(), PaginationConstants.DEFAULT_SORTED_BY)
            .toUpperCase(Locale.ROOT);

    String searchKey = normalize(query.searchKey());
    String searchPattern = searchKey == null ? null : toContainsPattern(searchKey);
    long requestedOffset = (page - 1L) * size;
    if (requestedOffset > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid pagination");

    long total = batchParticipantRepository.countByBatch(batch, searchPattern);
    int pages = total == 0 ? 0 : (int) ((total - 1) / size + 1);

    List<OrganizationBatchParticipantResponse> participants =
        batchParticipantRepository
            .findByBatch(batch, requestedOffset, size, searchPattern, sortKey, sortBy)
            .stream()
            .map(ListBatchParticipantUseCase::toResponse)
            .toList();

    log.info(
        "Batch participants retrieved: batchId={}, page={}, size={}, total={}",
        batchId,
        page,
        size,
        total);
    return new PageResponse<>(participants, page, size, total, pages);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    String normalized = normalize(value);
    return normalized == null ? defaultValue : normalized;
  }

  private static String toContainsPattern(String value) {
    String escapedValue =
        value
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escapedValue + "%";
  }

  private static OrganizationBatchParticipantResponse toResponse(
      BatchParticipantSummary participant) {
    return new OrganizationBatchParticipantResponse(
        participant.batchParticipantId().value(),
        participant.participantId().value(),
        participant.participantCode(),
        participant.departmentName(),
        participant.jobTitle(),
        participant.occupation(),
        participant.fullName(),
        participant.dateOfBirth(),
        participant.sex(),
        participant.identificationNumber(),
        participant.identificationNumberIssueDate(),
        participant.identificationNumberIssuePlace(),
        participant.ethnicity(),
        participant.subjectType(),
        participant.payerSource(),
        participant.bloodGroup(),
        participant.phone(),
        participant.province(),
        participant.ward(),
        participant.addressDetail(),
        participant.administrativeOccupation(),
        participant.workplaceOrSchool(),
        participant.healthExaminationReason(),
        participant.status(),
        participant.createdAt());
  }
}
