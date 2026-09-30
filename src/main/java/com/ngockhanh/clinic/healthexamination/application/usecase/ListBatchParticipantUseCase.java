package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationBatchParticipantResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantSummary;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListBatchParticipantUseCase {
	private static final List<String> ALLOWED_SORT_KEYS = List.of(
			"id", "participantId", "fullName", "createdAt", "departmentName", "jobTitle");
	private static final List<String> ALLOWED_SORT_DIRECTIONS = List.of("ASC", "DESC");

	private final HealthExaminationBatchRepository batchRepository;
	private final HealthExaminationBatchParticipantRepository batchParticipantRepository;

	@Transactional(readOnly = true)
	public PageResponse<OrganizationBatchParticipantResponse> execute(
			UUID organizationId, UUID batchId, OrganizationBatchParticipantQuery query) {
		if (query == null) throw new IllegalArgumentException("Participant list query is required");

		int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
		int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
		String sortKey = normalizeOrDefault(query.sortKey(), PaginationConstants.DEFAULT_SORTED_KEY);
		String sortBy = normalizeOrDefault(query.sortBy(), PaginationConstants.DEFAULT_SORTED_BY)
				.toUpperCase(Locale.ROOT);
		validateQuery(page, size, sortKey, sortBy);

		AggregateId organization = AggregateId.of(organizationId);
		AggregateId batch = AggregateId.of(batchId);

		batchRepository.findById(batch)
				.filter(found -> found.organizationId().equals(organization))
				.orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

		String searchKey = normalize(query.searchKey());
		String searchPattern = searchKey == null ? null : toContainsPattern(searchKey);
		long requestedOffset = (page - 1L) * size;
		if (requestedOffset > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pagination");

		long total = batchParticipantRepository.countByBatch(batch, searchPattern);
		int pages = total == 0 ? 0 : (int) ((total - 1) / size + 1);

		List<OrganizationBatchParticipantResponse> participants = batchParticipantRepository.findByBatch(
						batch, requestedOffset, size, searchPattern, sortKey, sortBy)
				.stream().map(ListBatchParticipantUseCase::toResponse).toList();

		log.info("Batch participants retrieved: batchId={}, page={}, size={}, total={}", batchId, page, size, total);
		return new PageResponse<>(participants, page, size, total, pages);
	}

	private static void validateQuery(int page, int size, String sortKey, String sortBy) {
		if (page < PaginationConstants.DEFAULT_PAGE_NUMBER || size < 1 || size > PaginationConstants.MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid pagination");
		}
		if (!ALLOWED_SORT_KEYS.contains(sortKey)) {
			throw new IllegalArgumentException("Invalid sort key");
		}
		if (!ALLOWED_SORT_DIRECTIONS.contains(sortBy)) {
			throw new IllegalArgumentException("Invalid sort direction");
		}
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
		String escapedValue = value.toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escapedValue + "%";
	}

	private static OrganizationBatchParticipantResponse toResponse(BatchParticipantSummary participant) {
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
