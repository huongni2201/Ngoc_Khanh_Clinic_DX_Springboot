package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateOrganizationUseCaseTest {
  private CreateOrganizationCommand command(String code, String taxCode) {
    return new CreateOrganizationCommand(
        code,
        "School",
        "SCHOOL",
        taxCode,
        "0901",
        "school@example.test",
        "Address",
        "Contact",
        null,
        "0902",
        "contact@example.test");
  }

  @Test
  void persistsAndAuditsOrganizationWithSeparateContactChannels() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    UUID actor = UUID.randomUUID();
    var result = new CreateOrganizationUseCase(repo, audit).execute(command("S1", null), actor);
    var captured = ArgumentCaptor.forClass(Organization.class);
    verify(repo).save(captured.capture());
    assertThat(result.code()).isEqualTo("S1");
    assertThat(result.organizationType()).isEqualTo("SCHOOL");
    assertThat(result.phone()).isEqualTo("0901");
    assertThat(result.contactPhone()).isEqualTo("0902");
    assertThat(result.taxCode()).isNull();
    verify(audit)
        .record(
            actor, "CREATE_ORGANIZATION", "ORGANIZATION", result.id(), null, Map.of("code", "S1"));
  }

  @Test
  void rejectsDuplicateOrganizationCode() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.existsByCode("S1", null)).thenReturn(true);
    assertThatThrownBy(
            () ->
                new CreateOrganizationUseCase(repo, audit)
                    .execute(command("S1", null), UUID.randomUUID()))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    verify(repo, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void allowsTheSameTaxCodeForDifferentOrganizations() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.existsByCode(anyString(), isNull())).thenReturn(false);
    var create = new CreateOrganizationUseCase(repo, audit);
    UUID actor = UUID.randomUUID();

    var first = create.execute(command("S1", "SHARED-TAX"), actor);
    var second = create.execute(command("S2", "SHARED-TAX"), actor);

    assertThat(first.taxCode()).isEqualTo("SHARED-TAX");
    assertThat(second.taxCode()).isEqualTo("SHARED-TAX");
    verify(repo, times(2)).save(any());
    verify(audit, times(2))
        .record(eq(actor), eq("CREATE_ORGANIZATION"), any(), any(), isNull(), any());
  }
}
