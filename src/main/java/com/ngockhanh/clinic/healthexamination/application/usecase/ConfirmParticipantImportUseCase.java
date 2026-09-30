package com.ngockhanh.clinic.healthexamination.application.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportConfirmResponse;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter.AuditEntry;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantRosterSnapshot;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfirmParticipantImportUseCase {
    private final HealthExaminationBatchRepository batches;
    private final HealthExaminationImportJobRepository jobs;
    private final HealthExaminationParticipantRepository participants;
    private final HealthExaminationBatchParticipantRepository batchParticipants;
    private final ParticipantImportAuditWriter auditWriter;

    @PreAuthorize("hasAuthority('CLINIC_MANAGER')")
    @Transactional
    public ParticipantImportConfirmResponse execute(UUID organizationId, UUID batchId, UUID importId,
                                                    UUID actorUserId) {
        if (organizationId == null || batchId == null || importId == null || actorUserId == null) {
            throw new IllegalArgumentException("Participant import confirmation details are required");
        }
        AggregateId organization = AggregateId.of(organizationId);
        AggregateId batch = AggregateId.of(batchId);
        AggregateId importJobId = AggregateId.of(importId);
        HealthExaminationBatchReference reference = batches.findByIdAndOrganizationIdForUpdate(batch, organization)
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
        HealthExaminationImportJob job = jobs.findByIdAndBatchIdForUpdate(importJobId, batch)
                .orElseThrow(() -> new ResourceNotFoundException("Participant import"));

        if (job.type() != ImportType.PARTICIPANT_LIST) {
            throw businessRule("Only participant-list imports can be confirmed here");
        }
        if (job.status() == ImportStatus.CONFIRMED) return response(job);
        if (!reference.status().allowsRosterImport()) {
            throw businessRule("Roster import is not allowed for this batch state");
        }
        if (job.status() != ImportStatus.VALIDATED) {
            throw businessRule("Participant import is not ready for confirmation");
        }

        Instant confirmedAt = Instant.now();
        job.confirm(AggregateId.of(actorUserId), confirmedAt);
        List<HealthExaminationImportRow> rows = job.confirmableRosterRows();
        List<IdentificationNumber> numbers = rows.stream()
                .map(HealthExaminationImportRow::getIdentificationNumber)
                .distinct()
                .sorted(Comparator.comparing(IdentificationNumber::value))
                .toList();

        Map<String, HealthExaminationParticipant> peopleByCccd = new HashMap<>();
        forEachChunk(numbers, chunk -> participants
                .findByOrganizationAndIdentificationNumbersForUpdate(organization, chunk)
                .forEach(person -> peopleByCccd.put(person.identificationNumber().value(), person)));

        List<AggregateId> personIds = peopleByCccd.values().stream()
                .map(HealthExaminationParticipant::id)
                .distinct()
                .sorted(Comparator.comparing(id -> id.value().toString()))
                .toList();
        Map<AggregateId, BatchParticipantRosterSnapshot> snapshotsByPerson = new HashMap<>();
        forEachChunk(personIds, chunk -> batchParticipants.findRosterSnapshotsForUpdate(batch, chunk)
                .forEach(snapshot -> snapshotsByPerson.put(snapshot.participantId(), snapshot)));

        List<AggregateId> existingBatchParticipantIds = snapshotsByPerson.values().stream()
                .map(BatchParticipantRosterSnapshot::batchParticipantId)
                .distinct()
                .sorted(Comparator.comparing(id -> id.value().toString()))
                .toList();
        Set<AggregateId> withHealthRecords = new HashSet<>();
        forEachChunk(existingBatchParticipantIds, chunk -> withHealthRecords.addAll(
                batchParticipants.findBatchParticipantIdsWithHealthRecords(chunk)));

        List<HealthExaminationParticipant> participantWrites = new ArrayList<>();
        List<BatchParticipantRosterSnapshot> newSnapshots = new ArrayList<>();
        List<BatchParticipantRosterSnapshot> updatedSnapshots = new ArrayList<>();
        for (HealthExaminationImportRow row : rows) {
            HealthExaminationParticipant existing = peopleByCccd.get(row.getIdentificationNumber().value());
            BatchParticipantRosterSnapshot oldSnapshot = existing == null ? null : snapshotsByPerson.get(existing.id());
            if (oldSnapshot != null && !oldSnapshot.identificationNumber().equals(row.getIdentificationNumber())) {
                throw stalePreview();
            }
            String currentFingerprint = ValidateParticipantImportUseCase.previewFingerprint(existing, oldSnapshot,
                    oldSnapshot != null && withHealthRecords.contains(oldSnapshot.batchParticipantId()), reference.startDate());
            if (!currentFingerprint.equals(row.getPreviewFingerprint())) throw stalePreview();

            ImportRowAction action = oldSnapshot == null ? ImportRowAction.CREATE
                    : ValidateParticipantImportUseCase.sameRoster(row, existing, oldSnapshot)
                            ? ImportRowAction.UNCHANGED : ImportRowAction.UPDATE;
            if (row.getAppliedAction() != action) throw stalePreview();

            HealthExaminationParticipant person = existing == null
                    ? createParticipant(organization, row)
                    : updateParticipant(existing, row);
            AggregateId batchParticipantId = oldSnapshot == null
                    ? AggregateId.of(UuidV7Generator.generate()) : oldSnapshot.batchParticipantId();
            row.resolve(person.id(), batchParticipantId, action);

            if (action == ImportRowAction.UPDATE && withHealthRecords.contains(batchParticipantId)) {
                row.addWarning("HEALTH_RECORD_SNAPSHOT_UNCHANGED");
            }
            if (action != ImportRowAction.UNCHANGED) {
                if (person != existing) participantWrites.add(person);
                BatchParticipantRosterSnapshot updated = snapshot(batchParticipantId, person, row, oldSnapshot);
                if (oldSnapshot == null) newSnapshots.add(updated);
                else updatedSnapshots.add(updated);
            }
        }

        try {
            if (!participantWrites.isEmpty()) participants.saveAll(participantWrites);
            if (!newSnapshots.isEmpty()) batchParticipants.insertRosterSnapshots(batch, newSnapshots);
            if (!updatedSnapshots.isEmpty()) batchParticipants.updateRosterSnapshots(batch, updatedSnapshots);
            jobs.save(job);
            ParticipantImportConfirmResponse response = response(job);
            auditWriter.record(new AuditEntry(UuidV7Generator.generate(), actorUserId,
                    confirmedAt, "PARTICIPANT_ROSTER_IMPORT_CONFIRMED", job.id().value(),
                    "{\"status\":\"VALIDATED\"}", String.format(java.util.Locale.ROOT,
                    "{\"status\":\"CONFIRMED\",\"totalRows\":%d,\"createdRows\":%d,\"updatedRows\":%d,\"unchangedRows\":%d}",
                    response.importedRows(), response.createdRows(), response.updatedRows(), response.unchangedRows())));
            return response;
        } catch (DuplicateKeyException | ConcurrentUpdateException conflict) {
            throw stalePreview();
        }
    }

    private HealthExaminationParticipant createParticipant(AggregateId organization,
                                                            HealthExaminationImportRow row) {
        AggregateId id = AggregateId.of(UuidV7Generator.generate());
        return HealthExaminationParticipant.create(id, organization, id.value().toString(),
                row.getIdentificationNumber(), row.getFullName(), row.getDateOfBirth(), row.getSex(),
                null, null, row.getOccupation());
    }

    private static HealthExaminationParticipant updateParticipant(HealthExaminationParticipant existing,
                                                                   HealthExaminationImportRow row) {
        String occupation = valueOr(row.getOccupation(), existing.occupation());
        if (existing.fullName().equals(row.getFullName()) && existing.dateOfBirth().equals(row.getDateOfBirth())
                && existing.sex().equals(row.getSex()) && java.util.Objects.equals(existing.occupation(), occupation)) {
            return existing;
        }
        return existing.reimport(existing.participantCode(), row.getIdentificationNumber(), row.getFullName(),
                row.getDateOfBirth(), row.getSex(), existing.departmentName(), existing.jobTitle(), occupation);
    }

    private static BatchParticipantRosterSnapshot snapshot(AggregateId batchParticipantId,
            HealthExaminationParticipant person, HealthExaminationImportRow row,
            BatchParticipantRosterSnapshot old) {
        return new BatchParticipantRosterSnapshot(batchParticipantId, person.id(), person.participantCode(),
                old == null ? row.getDepartmentName() : old.departmentName(),
                old == null ? row.getJobTitle() : old.jobTitle(), valueOr(row.getOccupation(),
                        old == null ? null : old.occupation()),
                row.getFullName(), row.getDateOfBirth(), row.getSex(), row.getIdentificationNumber(),
                valueOr(row.getIdentificationNumberIssueDate(), old == null ? null : old.identificationNumberIssueDate()),
                valueOr(row.getIdentificationNumberIssuePlace(), old == null ? null : old.identificationNumberIssuePlace()),
                valueOr(row.getEthnicity(), old == null ? null : old.ethnicity()),
                valueOr(row.getSubjectType(), old == null ? null : old.subjectType()),
                valueOr(row.getPayerSource(), old == null ? null : old.payerSource()),
                valueOr(row.getBloodGroup(), old == null ? null : old.bloodGroup()),
                valueOr(row.getPhone(), old == null ? null : old.phone()),
                valueOr(row.getProvince(), old == null ? null : old.province()),
                valueOr(row.getWard(), old == null ? null : old.ward()),
                valueOr(row.getAddressDetail(), old == null ? null : old.addressDetail()),
                valueOr(row.getAdministrativeOccupation(), old == null ? null : old.administrativeOccupation()),
                valueOr(row.getWorkplaceOrSchool(), old == null ? null : old.workplaceOrSchool()),
                valueOr(row.getHealthExaminationReason(), old == null ? null : old.healthExaminationReason()),
                valueOr(row.getRosterNote(), old == null ? null : old.rosterNote()));
    }

    private static ParticipantImportConfirmResponse response(HealthExaminationImportJob job) {
        List<HealthExaminationImportRow> rows = job.rows();
        int created = count(rows, ImportRowAction.CREATE);
        int updated = count(rows, ImportRowAction.UPDATE);
        int unchanged = count(rows, ImportRowAction.UNCHANGED);
        return new ParticipantImportConfirmResponse(job.id().value(), job.status().name(),
                created + updated + unchanged, created, updated, unchanged);
    }

    private static int count(List<HealthExaminationImportRow> rows, ImportRowAction action) {
        return (int) rows.stream().filter(row -> row.getAppliedAction() == action).count();
    }

    private static <T> void forEachChunk(List<T> values, java.util.function.Consumer<List<T>> consumer) {
        for (int start = 0; start < values.size(); start += 400) {
            consumer.accept(values.subList(start, Math.min(start + 400, values.size())));
        }
    }

    private static <T> T valueOr(T supplied, T existing) {
        return supplied == null ? existing : supplied;
    }

    private static BusinessRuleException businessRule(String message) {
        return new BusinessRuleException(message) { };
    }

    private static ConcurrentUpdateException stalePreview() {
        return new ConcurrentUpdateException("IMPORT_PREVIEW_STALE",
                "Import preview is stale; validate the roster again before confirming");
    }
}
