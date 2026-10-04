package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateOrganizationUseCaseTest {
  private CreateOrganizationCommand command(String code) {
    return new CreateOrganizationCommand(
        code,
        "School",
        "SCHOOL",
        null,
        "0901",
        "school@example.test",
        "Address",
        "Contact",
        null,
        "0902",
        "contact@example.test");
  }

  @Test
  void persistsSeparateOrganizationAndContactChannelsWithoutTaxCode() {
    var repo = mock(OrganizationRepository.class);
    var result = new CreateOrganizationUseCase(repo).execute(command("S1"));
    var captured = ArgumentCaptor.forClass(Organization.class);
    verify(repo).save(captured.capture());
    assertThat(result.code()).isEqualTo("S1");
    assertThat(result.organizationType()).isEqualTo("SCHOOL");
    assertThat(result.phone()).isEqualTo("0901");
    assertThat(result.contactPhone()).isEqualTo("0902");
    assertThat(result.taxCode()).isNull();
  }

  @Test
  void rejectsDuplicateOrganizationCode() {
    var repo = mock(OrganizationRepository.class);
    when(repo.existsByCode("S1", null)).thenReturn(true);
    assertThatThrownBy(() -> new CreateOrganizationUseCase(repo).execute(command("S1")))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    verify(repo, never()).save(any());
  }
}
