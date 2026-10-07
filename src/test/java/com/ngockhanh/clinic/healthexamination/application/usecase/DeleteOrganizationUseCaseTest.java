package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteOrganizationUseCaseTest {
  private final AggregateId id = new AggregateId(UUID.randomUUID());
  private final UUID actor = UUID.randomUUID();
  private final OrganizationRepository repository = mock(OrganizationRepository.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final DeleteOrganizationUseCase useCase =
      new DeleteOrganizationUseCase(repository, audit);

  private Organization organization(OrganizationStatus status, long version) {
    return Organization.restore(
        id,
        "School",
        null,
        "0901",
        "s@example.test",
        "Address",
        "Contact",
        "0902",
        "c@example.test",
        status,
        version);
  }

  private DeleteOrganizationCommand command(Long version) {
    return DeleteOrganizationCommand.builder().rowVersion(version).build();
  }

  @Test
  void deactivatesWithExpectedVersionAndAuditsBeforeAndAfter() {
    when(repository.findById(id))
        .thenReturn(
            Optional.of(organization(OrganizationStatus.ACTIVE, 3)),
            Optional.of(organization(OrganizationStatus.INACTIVE, 4)));

    useCase.execute(id.value(), command(3L), actor);

    verify(repository)
        .update(
            argThat(o -> o.status() == OrganizationStatus.INACTIVE && o.rowVersion() == 3), eq(3L));
    verify(audit)
        .record(
            actor,
            "DEACTIVATE_ORGANIZATION",
            "ORGANIZATION",
            id.value(),
            Map.of("status", "ACTIVE", "rowVersion", 3L),
            Map.of("status", "INACTIVE", "rowVersion", 4L));
  }

  @Test
  void alreadyInactiveWithCurrentVersionIsANoOp() {
    when(repository.findById(id))
        .thenReturn(Optional.of(organization(OrganizationStatus.INACTIVE, 4)));

    useCase.execute(id.value(), command(4L), actor);

    verify(repository, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void alreadyInactiveWithStaleVersionIsAConflict() {
    when(repository.findById(id))
        .thenReturn(Optional.of(organization(OrganizationStatus.INACTIVE, 4)));

    assertThatThrownBy(() -> useCase.execute(id.value(), command(3L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repository, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void staleVersionIsRejectedBeforeMutation() {
    when(repository.findById(id))
        .thenReturn(Optional.of(organization(OrganizationStatus.ACTIVE, 3)));

    assertThatThrownBy(() -> useCase.execute(id.value(), command(2L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repository, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void versionLostBetweenReadAndWriteIsAConflictWithoutAudit() {
    when(repository.findById(id))
        .thenReturn(Optional.of(organization(OrganizationStatus.ACTIVE, 3)));
    doThrow(new ConcurrentUpdateException()).when(repository).update(any(), eq(3L));

    assertThatThrownBy(() -> useCase.execute(id.value(), command(3L), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
    verifyNoInteractions(audit);
  }

  @Test
  void missingOrganizationRaisesNotFoundWithoutMutation() {
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(id.value(), command(0L), actor))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(repository, never()).update(any(), anyLong());
    verifyNoInteractions(audit);
  }

  @Test
  void rejectsNullArgumentsAndMissingOrNegativeVersionBeforeReading() {
    assertThatThrownBy(() -> useCase.execute(null, command(0L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), null, actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), command(0L), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), command(null), actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id.value(), command(-1L), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository, audit);
  }
}
