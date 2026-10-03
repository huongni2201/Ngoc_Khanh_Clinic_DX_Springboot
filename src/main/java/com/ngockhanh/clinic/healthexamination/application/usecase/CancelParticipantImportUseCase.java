package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter.AuditEntry;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CancelParticipantImportUseCase {
	private final HealthExaminationBatchRepository batches;
	private final HealthExaminationImportJobRepository jobs;
	private final ParticipantImportAuditWriter auditWriter;

	@Transactional
	public ParticipantImportSummaryResponse execute(
			UUID organizationId, UUID batchId, UUID importId,
			UUID actorUserId) {

		if (organizationId == null || batchId == null || importId == null || actorUserId == null) {
			throw new IllegalArgumentException("Participant import cancellation details are required");
		}
		AggregateId organization = AggregateId.of(organizationId);
		AggregateId batch = AggregateId.of(batchId);
		AggregateId importJobId = AggregateId.of(importId);

		batches.findByIdAndOrganizationId(batch, organization)
				.orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

		HealthExaminationImportJob job = jobs.findByIdAndBatchIdForUpdate(importJobId, batch)
				.orElseThrow(() -> new ResourceNotFoundException("Participant import"));

		if (job.type() != ImportType.PARTICIPANT_LIST) {
			throw new BusinessRuleException("Only participant-list imports can be canceled here") {
			};
		}

		if (job.status() == ImportStatus.CANCELED) {
			return ParticipantImportSummaryResponse.from(job, List.of());
		}

		ImportStatus previousStatus = job.status();
		job.cancel();

		jobs.save(job);

		Instant occurredAt = Instant.now();

		auditWriter.record(new AuditEntry(UuidV7Generator.generate(), actorUserId, occurredAt,
				"PARTICIPANT_ROSTER_IMPORT_CANCELED", job.id().value(),
				"{\"status\":\"" + previousStatus.name() + "\"}", "{\"status\":\"CANCELED\"}"));
		return ParticipantImportSummaryResponse.from(job, List.of());
	}
}
