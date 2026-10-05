package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationCrudUseCaseTest {
  private final AggregateId id = new AggregateId(UUID.randomUUID());
  private final UUID actor = UUID.randomUUID();

  private Organization organization(long version) {
    return Organization.restore(
        id,
        "S1",
        "School",
        "SCHOOL",
        "TAX",
        "0901",
        "s@example.test",
        "Address",
        "Contact",
        null,
        "0902",
        "c@example.test",
        "ACTIVE",
        version);
  }

  private UpdateOrganizationCommand command(long version) {
    return UpdateOrganizationCommand.builder()
        .code("S1")
        .name("Renamed")
        .organizationType("SCHOOL")
        .taxCode("TAX")
        .phone("0901")
        .email("s@example.test")
        .address("Address")
        .contactFullName("Contact")
        .contactPosition(null)
        .contactPhone("0902")
        .contactEmail("c@example.test")
        .rowVersion(version)
        .build();
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
    verify(repo).existsByCode("S1", id);
    verify(audit)
        .record(
            actor,
            "UPDATE_ORGANIZATION",
            "ORGANIZATION",
            id.value(),
            Map.of("code", "S1", "rowVersion", 3L),
            Map.of("code", "S1", "rowVersion", 4L));
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
  void duplicateCodeDoesNotUpdateOrAudit() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    when(repo.existsByCode("S1", id)).thenReturn(true);

    assertThatThrownBy(
            () -> new UpdateOrganizationUseCase(repo, audit).execute(id.value(), command(3), actor))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    verify(repo, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }
}
