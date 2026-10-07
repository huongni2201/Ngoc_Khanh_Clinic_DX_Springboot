package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;

class UpdateOrganizationUseCaseTest {
  private final AggregateId id = new AggregateId(UUID.randomUUID());
  private final UUID actor = UUID.randomUUID();

  private Organization organization(long version) {
    return Organization.restore(
        id,
        "School",
        "TAX",
        "0901",
        "s@example.test",
        "Address",
        "Contact",
        "0902",
        "c@example.test",
        OrganizationStatus.ACTIVE,
        version);
  }

  private UpdateOrganizationCommand command(long version) {
    return commandBuilder(version).build();
  }

  private UpdateOrganizationCommand.UpdateOrganizationCommandBuilder commandBuilder(long version) {
    return UpdateOrganizationCommand.builder()
        .name("Renamed")
        .taxCode("TAX")
        .phone("0901")
        .email("s@example.test")
        .address("Address")
        .contactFullName("Contact")
        .contactPhone("0902")
        .contactEmail("c@example.test")
        .rowVersion(version);
  }

  @Test
  void rejectsStaleClientVersionBeforeMutation() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    assertThatThrownBy(
            () -> new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(2), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repo, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void updatesUsingExpectedVersionAndReturnsReloadedVersion() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)), Optional.of(organization(4)));
    var result = new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(3), actor);
    verify(repo).update(argThat(o -> "Renamed".equals(o.name())), eq(3L));
    verify(repo).existsByTaxCode("TAX", id);
    verify(audit)
        .record(
            actor,
            "UPDATE_ORGANIZATION",
            "ORGANIZATION",
            id.value(),
            Map.of("taxCode", "TAX", "rowVersion", 3L),
            Map.of("taxCode", "TAX", "rowVersion", 4L));
    assertThat(result.rowVersion()).isEqualTo(4);
  }

  @Test
  void missingOrganizationRaisesNotFoundWithoutAudit() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(0), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void duplicateTaxCodeDoesNotUpdateOrAudit() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    when(repo.existsByTaxCode("TAX", id)).thenReturn(true);

    assertThatThrownBy(
            () -> new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(3), actor))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    verify(repo, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsNullArgumentsWithoutTouchingTheRepository() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    var useCase = new UpdateOrganizationUseCase(repo, audit);

    assertThatThrownBy(() -> useCase.execute(null, command(0), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), command(0), null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repo, audit);
  }

  @Test
  void missingRowVersionIsAConflictBeforeMutation() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    var withoutVersion = commandBuilder(3).rowVersion(null).build();

    assertThatThrownBy(
            () ->
                new UpdateOrganizationUseCase(repo, audit)
                    .execute(id.value(), withoutVersion, actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repo, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsVersionLostBetweenReadAndWrite() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    doThrow(new ConcurrentUpdateException()).when(repo).update(any(), eq(3L));

    assertThatThrownBy(
            () -> new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(3), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void keepsStatusAndAllowsClearingOptionalFields() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)), Optional.of(organization(4)));
    var clearing = commandBuilder(3).taxCode(null).build();

    new UpdateOrganizationUseCase(repo, audit).execute(id.value(), clearing, actor);

    verify(repo)
        .update(
            argThat(
                o ->
                    o.taxCode() == null
                        && o.status() == OrganizationStatus.ACTIVE
                        && id.equals(o.id())),
            eq(3L));
  }

  @Test
  void keepingTheSameTaxCodeIsAllowedBecauseTheOwnRowIsExcluded() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)), Optional.of(organization(4)));
    when(repo.existsByTaxCode("TAX", id)).thenReturn(false);

    new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(3), actor);

    verify(repo).existsByTaxCode("TAX", id);
    verify(repo).update(any(), eq(3L));
  }
}
