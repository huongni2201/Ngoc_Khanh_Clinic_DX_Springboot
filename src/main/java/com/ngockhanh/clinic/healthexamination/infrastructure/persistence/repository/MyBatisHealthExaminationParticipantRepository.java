package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationParticipantRepository
    implements HealthExaminationParticipantRepository {
  private final HealthExaminationParticipantMyBatisMapper mapper;

  public Optional<HealthExaminationParticipant> findById(AggregateId id) {
    return Optional.ofNullable(mapper.findById(id.value()))
        .map(r -> domain(r, mapper.findServices(List.of(r.id()))));
  }

  @Override
  public Optional<HealthExaminationParticipant> findInAgreement(
      AggregateId agreementId, AggregateId id) {
    return Optional.ofNullable(mapper.findInAgreement(agreementId.value(), id.value()))
        .map(r -> domain(r, mapper.findServices(List.of(r.id()))));
  }

  @Override
  public List<HealthExaminationParticipant> findManyInAgreementForUpdate(
      AggregateId agreementId, Collection<AggregateId> ids) {
    if (ids.isEmpty()) return List.of();
    return withServices(
        mapper.findManyInAgreementForUpdate(
            agreementId.value(), ids.stream().map(AggregateId::value).toList()));
  }

  @Override
  public List<HealthExaminationParticipant> findManyInAgreement(
      AggregateId agreementId, Collection<AggregateId> ids) {
    if (ids.isEmpty()) return List.of();
    return withServices(
        mapper.findManyInAgreement(
            agreementId.value(), ids.stream().map(AggregateId::value).toList()));
  }

  private List<HealthExaminationParticipant> withServices(
      List<HealthExaminationParticipantRecord> headers) {
    if (headers.isEmpty()) return List.of();
    Map<UUID, List<HealthExaminationParticipantServiceRecord>> servicesByParticipant =
        mapper
            .findServices(headers.stream().map(HealthExaminationParticipantRecord::id).toList())
            .stream()
            .collect(
                Collectors.groupingBy(HealthExaminationParticipantServiceRecord::participantId));
    return headers.stream()
        .map(header -> domain(header, servicesByParticipant.getOrDefault(header.id(), List.of())))
        .toList();
  }

  @Override
  public void insert(HealthExaminationParticipant participant) {
    if (mapper.insertMany(List.of(record(participant))) != 1)
      throw new IllegalStateException("Participant was not inserted");
  }

  @Override
  public boolean identityTakenByOther(
      AggregateId agreementId, IdentificationNumber identity, AggregateId excludeId) {
    return mapper.identityTakenByOther(agreementId.value(), identity.value(), value(excludeId));
  }

  public void save(HealthExaminationParticipant p, long expectedVersion) {
    saveWithoutServices(p, expectedVersion);
    saveServices(p);
  }

  @Override
  public void saveWithoutServices(HealthExaminationParticipant p, long expectedVersion) {
    if (mapper.update(record(p), expectedVersion) != 1) throw new ConcurrentUpdateException();
  }

  private void saveServices(HealthExaminationParticipant p) {
    if (p.services().isEmpty()) return;
    var existing =
        mapper.findServices(List.of(p.getId().value())).stream()
            .map(HealthExaminationParticipantServiceRecord::id)
            .collect(Collectors.toSet());
    var rows =
        p.services().stream()
            .map(
                s ->
                    HealthExaminationParticipantServiceRecord.builder()
                        .id(s.id().value())
                        .agreementId(s.agreementId().value())
                        .participantId(s.participantId().value())
                        .agreementServiceId(s.agreementServiceId().value())
                        .isPerformed(s.performed())
                        .serviceRequestId(value(s.serviceRequestId()))
                        .unitPriceSnapshot(s.unitPriceSnapshot().amount())
                        .recordedBy(s.recordedBy().value())
                        .recordedAt(s.recordedAt())
                        .createdAt(s.createdAt())
                        .updatedAt(s.updatedAt())
                        .rowVersion(s.rowVersion())
                        .build())
            .toList();
    var added = rows.stream().filter(r -> !existing.contains(r.id())).toList();
    var changed = rows.stream().filter(r -> existing.contains(r.id())).toList();
    if (!added.isEmpty() && mapper.insertServices(added) != added.size())
      throw new IllegalStateException("Participant services were not inserted");
    if (!changed.isEmpty() && mapper.updateServices(changed) != changed.size())
      throw new ConcurrentUpdateException();
  }

  @Override
  public void insertMany(List<HealthExaminationParticipant> participants) {
    if (participants.isEmpty()) return;
    var rows =
        participants.stream().map(MyBatisHealthExaminationParticipantRepository::record).toList();
    if (mapper.insertMany(rows) != rows.size())
      throw new IllegalStateException("Participants were not inserted");
  }

  @Override
  public long countInAgreement(AggregateId agreementId) {
    return mapper.countInAgreement(agreementId.value());
  }

  @Override
  public List<IdentificationNumber> findExistingIdentities(
      AggregateId agreementId, List<IdentificationNumber> identities) {
    if (identities.isEmpty()) return List.of();
    return mapper
        .findExistingIdentities(
            agreementId.value(), identities.stream().map(IdentificationNumber::value).toList())
        .stream()
        .map(IdentificationNumber::of)
        .toList();
  }

  @Override
  public List<ParticipantScope> findScopes(
      AggregateId organizationId, Collection<AggregateId> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper
        .findScopes(organizationId.value(), ids.stream().map(AggregateId::value).toList())
        .stream()
        .map(
            v ->
                new ParticipantScope(
                    id(v.id()),
                    id(v.agreementId()),
                    RosterStatus.valueOf(v.rosterStatus()),
                    AgreementStatus.valueOf(v.agreementStatus())))
        .toList();
  }

  private static HealthExaminationParticipant domain(
      HealthExaminationParticipantRecord r,
      List<HealthExaminationParticipantServiceRecord> services) {
    return HealthExaminationParticipant.restore(
        id(r.id()),
        id(r.organizationId()),
        id(r.agreementId()),
        new Roster(
            r.participantCode(),
            r.fullName(),
            r.dateOfBirth(),
            r.sex(),
            IdentificationNumber.of(r.identificationNumber()),
            r.identificationIssueDate(),
            r.identificationIssuePlace(),
            r.ethnicity(),
            r.phone(),
            r.email(),
            r.address(),
            r.workplace(),
            r.departmentName(),
            r.positionName(),
            r.note()),
        new Progress(
            id(r.patientId()),
            RosterStatus.valueOf(r.rosterStatus()),
            ReconciliationStatus.valueOf(r.serviceReconciliationStatus()),
            id(r.servicesReconciledBy()),
            r.servicesReconciledAt(),
            r.preparedAt()),
        id(r.importJobId()),
        r.sourceRowNumber(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion(),
        services.stream()
            .map(
                s ->
                    new HealthExaminationParticipantService(
                        id(s.id()),
                        id(s.agreementId()),
                        id(s.participantId()),
                        id(s.agreementServiceId()),
                        s.isPerformed(),
                        id(s.serviceRequestId()),
                        new Money(s.unitPriceSnapshot(), "VND"),
                        id(s.recordedBy()),
                        s.recordedAt(),
                        s.createdAt(),
                        s.updatedAt(),
                        s.rowVersion()))
            .toList());
  }

  private static HealthExaminationParticipantRecord record(HealthExaminationParticipant p) {
    var r = p.getRoster();
    return HealthExaminationParticipantRecord.builder()
        .id(p.getId().value())
        .organizationId(p.getOrganizationId().value())
        .agreementId(p.getAgreementId().value())
        .participantCode(r.participantCode())
        .fullName(r.fullName())
        .dateOfBirth(r.dateOfBirth())
        .sex(r.sex())
        .identificationNumber(r.identificationNumber().value())
        .identificationIssueDate(r.identificationIssueDate())
        .identificationIssuePlace(r.identificationIssuePlace())
        .ethnicity(r.ethnicity())
        .phone(r.phone())
        .email(r.email())
        .address(r.address())
        .workplace(r.workplace())
        .departmentName(r.departmentName())
        .positionName(r.positionName())
        .note(r.note())
        .patientId(value(p.getPatientId()))
        .rosterStatus(p.getRosterStatus().name())
        .serviceReconciliationStatus(p.getReconciliationStatus().name())
        .servicesReconciledBy(value(p.getReconciledBy()))
        .servicesReconciledAt(p.getReconciledAt())
        .importJobId(value(p.getImportJobId()))
        .sourceRowNumber(p.getSourceRowNumber())
        .preparedAt(p.getPreparedAt())
        .createdAt(p.getCreatedAt())
        .updatedAt(p.getUpdatedAt())
        .rowVersion(p.getRowVersion())
        .build();
  }

  private static AggregateId id(UUID id) {
    return id == null ? null : AggregateId.of(id);
  }

  private static UUID value(AggregateId id) {
    return id == null ? null : id.value();
  }
}
