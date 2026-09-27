package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportPreview;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchParticipantId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.IdGenerator;

@Service
@RequiredArgsConstructor
public class ConfirmParticipantImportUseCase {
    private final ParticipantImportBatchQuery batchQuery;
    private final HealthExaminationImportJobRepository importJobRepository;
    private final HealthExaminationParticipantRepository participantRepository;
    private final HealthExaminationBatchParticipantRepository batchParticipantRepository;
    private final IdGenerator idGenerator;

    @Transactional
    public ParticipantImportPreview execute(UUID organizationId, UUID batchId, UUID importId, UUID actorUserId) {
        var batch = batchQuery.findById(batchId)
                .filter(found -> found.organizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        HealthExaminationImportJob job = importJobRepository.findByIdForUpdate(importId)
                .filter(found -> found.batchId().equals(batchId))
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));
        if (job.isConfirmed()) return StageParticipantImportUseCase.toPreview(job);

        List<HealthExaminationImportRow> rows = job.confirmableRosterRows();
        Map<String, HealthExaminationParticipant> byCode = new HashMap<>();
        participantRepository.findByOrganizationAndCodes(batch.organizationId(), rows.stream()
                        .map(HealthExaminationImportRow::participantCode).toList())
                .forEach(participant -> byCode.put(participant.participantCode(), participant));
        Map<String, HealthExaminationParticipant> byIdentification = new HashMap<>();
        participantRepository.findByOrganizationAndIdentificationNumbers(batch.organizationId(), rows.stream()
                        .map(HealthExaminationImportRow::identificationNumber).toList())
                .forEach(participant -> byIdentification.put(participant.identificationNumber().value(), participant));
        Set<UUID> matchedParticipantIds = new HashSet<>();
        byCode.values().forEach(participant -> matchedParticipantIds.add(participant.id()));
        byIdentification.values().forEach(participant -> matchedParticipantIds.add(participant.id()));
        Set<UUID> alreadyInBatch = batchParticipantRepository.findParticipantIdsByBatch(batchId, matchedParticipantIds);

        for (HealthExaminationImportRow row : rows) {
            HealthExaminationParticipant codeMatch = byCode.get(row.participantCode());
            HealthExaminationParticipant identityMatch = byIdentification.get(row.identificationNumber().value());
            if (codeMatch != null && identityMatch != null && !codeMatch.id().equals(identityMatch.id())) {
                row.reject("PARTICIPANT_IDENTITY_CONFLICT");
                continue;
            }
            HealthExaminationParticipant existing = identityMatch == null ? codeMatch : identityMatch;
            if (existing != null && alreadyInBatch.contains(existing.id())) {
                row.reject("DUPLICATED_IN_BATCH");
                continue;
            }
            if (codeMatch != null && !codeMatch.id().equals(existing.id())) {
                row.reject("PARTICIPANT_CODE_CONFLICT");
                continue;
            }

            HealthExaminationParticipant participant;
            if (existing == null) {
                var snapshot = row.administrativeSnapshot();
                participant = HealthExaminationParticipant.create(idGenerator.next(), batch.organizationId(),
                        row.participantCode(), row.identificationNumber(), snapshot.fullName(), snapshot.dateOfBirth(),
                        snapshot.sex(), row.departmentName(), row.jobTitle(), row.occupation());
            } else {
                try {
                    participant = existing.reimport(row.participantCode(), row.identificationNumber(),
                            row.administrativeSnapshot().fullName(), row.administrativeSnapshot().dateOfBirth(),
                            row.administrativeSnapshot().sex(), row.departmentName(), row.jobTitle(), row.occupation());
                } catch (DomainRuleViolation identityConflict) {
                    row.reject("LINKED_PARTICIPANT_IDENTITY_REVIEW");
                    continue;
                }
            }
            participantRepository.save(participant);

            UUID batchParticipantId = idGenerator.next();
            HealthExaminationBatchParticipant batchParticipant = HealthExaminationBatchParticipant.create(
                    new HealthExaminationBatchParticipantId(batchParticipantId), batchId, participant.id(),
                    row.administrativeSnapshot(), row.participantCode(), row.departmentName(), row.jobTitle(), row.occupation());
            batchParticipantRepository.save(batchParticipant);
            row.resolve(participant.id(), batchParticipantId);
        }

        job.confirm(actorUserId, Instant.now());
        importJobRepository.save(job);
        return StageParticipantImportUseCase.toPreview(job);
    }
}
