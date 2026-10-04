package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationCrudUseCaseTest {
  private final AggregateId id = new AggregateId(UUID.randomUUID());

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
    return new UpdateOrganizationCommand(
        "S1",
        "Renamed",
        "SCHOOL",
        "TAX",
        "0901",
        "s@example.test",
        "Address",
        "Contact",
        null,
        "0902",
        "c@example.test",
        version);
  }

  @Test
  void rejectsStaleClientVersionBeforeMutation() {
    var repo = mock(OrganizationRepository.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)));
    assertThatThrownBy(() -> new UpdateOrganizationUseCase(repo).execute(id.value(), command(2)))
        .isInstanceOf(ConcurrentUpdateException.class);
    verify(repo, never()).update(any(), anyLong());
  }

  @Test
  void updatesUsingExpectedVersionAndReturnsReloadedVersion() {
    var repo = mock(OrganizationRepository.class);
    when(repo.findById(id)).thenReturn(Optional.of(organization(3)), Optional.of(organization(4)));
    var result = new UpdateOrganizationUseCase(repo).execute(id.value(), command(3));
    verify(repo).update(argThat(o -> "Renamed".equals(o.name())), eq(3L));
    assertThat(result.rowVersion()).isEqualTo(4);
  }

  @Test
  void deactivationPreservesIdentityAndContacts() {
    var repo = mock(OrganizationRepository.class);
    var current = organization(3);
    when(repo.findById(id)).thenReturn(Optional.of(current));
    new DeactivateOrganizationUseCase(repo).execute(id.value());
    verify(repo).update(current.deactivate(), 3);
  }
}
