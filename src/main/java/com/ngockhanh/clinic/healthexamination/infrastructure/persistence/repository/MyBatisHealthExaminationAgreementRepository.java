package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationAgreementMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationAgreementRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationAgreementServiceRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationAgreementRepository
    implements HealthExaminationAgreementRepository {
  /** Key of the advisory lock that serialises agreement code generation. */
  private static final long CODE_SEQUENCE_LOCK_KEY = 0x4E4B43_42415443L;

  private final HealthExaminationAgreementMyBatisMapper mapper;

  @Override
  public Optional<AgreementDetails> findDetails(UUID org, UUID id, boolean lock) {
    return Optional.ofNullable(mapper.findScoped(org, id, lock))
        .map(
            r ->
                new AgreementDetails(
                    HealthExaminationAgreement.restoreConfiguration(
                        new AggregateId(r.id()),
                        new AggregateId(r.organizationId()),
                        r.agreementCode(),
                        r.name(),
                        r.validFrom(),
                        r.validTo(),
                        mapper.findServices(id).stream()
                            .map(
                                s ->
                                    new HealthExaminationAgreementService(
                                        new AggregateId(s.id()),
                                        new AggregateId(s.serviceId()),
                                        new AggregateId(s.agreementId()),
                                        new Money(s.referencePriceSnapshot(), "VND"),
                                        new Money(s.negotiatedPrice(), "VND"),
                                        s.displayOrder(),
                                        s.active(),
                                        s.rowVersion()))
                            .toList(),
                        AgreementStatus.valueOf(r.status()),
                        r.rowVersion(),
                        r.deletedAt()),
                    r.createdBy(),
                    r.createdAt(),
                    r.updatedAt()));
  }

  @Override
  public void insert(HealthExaminationAgreement agreement, UUID actor) {
    if (mapper.insert(record(agreement, actor)) != 1)
      throw new IllegalStateException("Agreement was not inserted");
    insertServices(agreement.services());
  }

  private void insertServices(List<HealthExaminationAgreementService> services) {
    var rows =
        services.stream()
            .map(
                s ->
                    HealthExaminationAgreementServiceRecord.builder()
                        .id(s.id().value())
                        .agreementId(s.agreementId().value())
                        .serviceId(s.serviceId().value())
                        .referencePriceSnapshot(s.referencePriceSnapshot().amount())
                        .negotiatedPrice(s.negotiatedPrice().amount())
                        .displayOrder(s.displayOrder())
                        .active(s.active())
                        .createdAt(null)
                        .updatedAt(null)
                        .rowVersion(s.rowVersion())
                        .build())
            .toList();
    if (mapper.insertServices(rows) != rows.size())
      throw new IllegalStateException("Agreement services were not inserted");
  }

  private HealthExaminationAgreementRecord record(HealthExaminationAgreement a, UUID actor) {
    return HealthExaminationAgreementRecord.builder()
        .id(a.id().value())
        .organizationId(a.organizationId().value())
        .agreementCode(a.code())
        .name(a.name())
        .validFrom(a.validFrom())
        .validTo(a.validTo())
        .status(a.status().name())
        .createdBy(actor)
        .createdAt(null)
        .updatedAt(null)
        .rowVersion(a.rowVersion())
        .deletedAt(a.deletedAt())
        .build();
  }

  @Override
  public void update(HealthExaminationAgreement agreement, long expectedRowVersion) {
    if (mapper.updateHeader(record(agreement, null), expectedRowVersion) != 1)
      throw new ConcurrentUpdateException();
    syncServices(agreement);
  }

  /**
   * Applies the service differences without recreating services that keep their identifier.
   *
   * <p>Display order is unique per agreement, so a retained service whose order changes is first
   * moved above every old and new order and then written to its final order. The temporary move
   * does not change the row version; the final write increments it once.
   */
  private void syncServices(HealthExaminationAgreement agreement) {
    UUID agreementId = agreement.id().value();
    Map<UUID, HealthExaminationAgreementServiceRecord> existing = new HashMap<>();
    for (var row : mapper.findServices(agreementId)) existing.put(row.id(), row);
    var target = agreement.services();
    Set<UUID> targetIds = target.stream().map(s -> s.id().value()).collect(Collectors.toSet());

    List<UUID> removed = existing.keySet().stream().filter(id -> !targetIds.contains(id)).toList();
    if (!removed.isEmpty() && mapper.deleteServices(agreementId, removed) != removed.size())
      throw new ConcurrentUpdateException();

    var retained = target.stream().filter(s -> existing.containsKey(s.id().value())).toList();
    var reordered =
        retained.stream()
            .filter(s -> existing.get(s.id().value()).displayOrder() != s.displayOrder())
            .toList();
    long highestOrder = 0;
    for (var row : existing.values()) highestOrder = Math.max(highestOrder, row.displayOrder());
    for (var service : target) highestOrder = Math.max(highestOrder, service.displayOrder());
    for (var service : reordered) {
      long temporaryOrder = highestOrder + service.displayOrder();
      if (temporaryOrder > Integer.MAX_VALUE)
        throw new IllegalStateException("Agreement service display order is out of range");
      if (mapper.moveServiceOrder(agreementId, service.id().value(), (int) temporaryOrder) != 1)
        throw new ConcurrentUpdateException();
    }
    for (var service : retained) {
      var current = existing.get(service.id().value());
      boolean priceChanged =
          current.negotiatedPrice().compareTo(service.negotiatedPrice().amount()) != 0;
      boolean orderChanged = current.displayOrder() != service.displayOrder();
      if (!priceChanged && !orderChanged) continue;
      if (mapper.updateService(
              agreementId,
              service.id().value(),
              service.negotiatedPrice().amount(),
              service.displayOrder(),
              current.rowVersion())
          != 1) throw new ConcurrentUpdateException();
    }

    var added = target.stream().filter(s -> !existing.containsKey(s.id().value())).toList();
    if (!added.isEmpty()) insertServices(added);
  }

  @Override
  public void softDelete(HealthExaminationAgreement agreement, long expectedRowVersion) {
    if (agreement.deletedAt() == null)
      throw new IllegalArgumentException("Agreement must be marked deleted before it is stored");
    if (mapper.softDelete(
            agreement.id().value(),
            agreement.organizationId().value(),
            expectedRowVersion,
            agreement.deletedAt())
        != 1) throw new ConcurrentUpdateException();
  }

  @Override
  public void updateStatus(
      HealthExaminationAgreement agreement,
      AgreementStatus expectedStatus,
      long expectedRowVersion) {
    if (agreement == null || expectedStatus == null)
      throw new IllegalArgumentException("Agreement and expected status are required");
    if (mapper.updateStatus(
            agreement.id().value(),
            agreement.organizationId().value(),
            agreement.status().name(),
            expectedStatus.name(),
            expectedRowVersion)
        != 1) throw new ConcurrentUpdateException();
  }

  @Override
  public Set<UUID> findReferencedAgreementServiceIds(
      UUID agreementId, Collection<UUID> serviceLineIds) {
    if (serviceLineIds.isEmpty()) return Set.of();
    return new HashSet<>(mapper.findReferencedAgreementServiceIds(agreementId, serviceLineIds));
  }

  @Override
  public long highestCodeSequence(String prefix) {
    mapper.lockCodeSequence(CODE_SEQUENCE_LOCK_KEY);
    return mapper.highestCodeSequence(prefix);
  }

  @Override
  public boolean hasParticipants(UUID agreementId) {
    return mapper.hasParticipants(agreementId);
  }

  @Override
  public boolean hasSessions(UUID agreementId) {
    return mapper.hasSessions(agreementId);
  }

  @Override
  public List<AgreementSummary> findPage(
      UUID org, long offset, int limit, String pattern, String key, String direction) {
    return mapper.findPage(org, offset, limit, pattern, key, direction).stream()
        .map(
            v ->
                new AgreementSummary(
                    v.id(),
                    v.agreementCode(),
                    v.agreementName(),
                    v.validFrom(),
                    v.validTo(),
                    AgreementStatus.valueOf(v.status()),
                    v.createdAt(),
                    v.updatedAt(),
                    v.rowVersion()))
        .toList();
  }

  @Override
  public long count(UUID org, String pattern) {
    return mapper.count(org, pattern);
  }
}
