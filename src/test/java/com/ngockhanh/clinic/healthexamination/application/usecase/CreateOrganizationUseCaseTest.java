package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateOrganizationUseCaseTest {
  private CreateOrganizationCommand command(String taxCode) {
    return CreateOrganizationCommand.builder()
        .name("School")
        .taxCode(taxCode)
        .phone("0901")
        .email("school@example.test")
        .address("Address")
        .contactFullName("Contact")
        .contactPhone("0902")
        .contactEmail("contact@example.test")
        .build();
  }

  @Test
  void persistsAndAuditsOrganizationWithSeparateContactChannels() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    UUID actor = UUID.randomUUID();
    var result = new CreateOrganizationUseCase(repo, audit).execute(command(null), actor);
    var captured = ArgumentCaptor.forClass(Organization.class);
    verify(repo).save(captured.capture());
    assertThat(result.phone()).isEqualTo("0901");
    assertThat(result.contactPhone()).isEqualTo("0902");
    assertThat(result.taxCode()).isNull();
    verify(audit)
        .record(
            actor,
            "CREATE_ORGANIZATION",
            "ORGANIZATION",
            result.id(),
            null,
            java.util.Collections.singletonMap("taxCode", null));
  }

  @Test
  void propagatesAuditFailure() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    var failure = new IllegalStateException("Audit unavailable");
    doThrow(failure).when(audit).record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                new CreateOrganizationUseCase(repo, audit)
                    .execute(command("TAX-01"), UUID.randomUUID()))
        .isSameAs(failure);
  }

  @Test
  void rejectsDuplicateTaxCode() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    when(repo.existsByTaxCode("TAX-01", null)).thenReturn(true);
    assertThatThrownBy(
            () ->
                new CreateOrganizationUseCase(repo, audit)
                    .execute(command("TAX-01"), UUID.randomUUID()))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    verify(repo, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void allowsOrganizationsWithoutTaxCodes() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    var create = new CreateOrganizationUseCase(repo, audit);
    UUID actor = UUID.randomUUID();

    var first = create.execute(command(null), actor);
    var second = create.execute(command(null), actor);

    assertThat(first.taxCode()).isNull();
    assertThat(second.taxCode()).isNull();
    verify(repo, times(2)).save(any());
    verify(audit, times(2))
        .record(eq(actor), eq("CREATE_ORGANIZATION"), any(), any(), isNull(), any());
  }

  @Test
  void rejectsNullCommandAndNullActor() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    var create = new CreateOrganizationUseCase(repo, audit);

    assertThatThrownBy(() -> create.execute(null, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> create.execute(command(null), null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repo, audit);
  }

  @Test
  void createsActiveOrganizationAtVersionZero() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);

    var result =
        new CreateOrganizationUseCase(repo, audit).execute(command(null), UUID.randomUUID());

    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(result.rowVersion()).isZero();
    assertThat(result.id()).isNotNull();
  }

  @Test
  void invalidDetailsAreRejectedWithoutWriteOrAudit() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);
    var create = new CreateOrganizationUseCase(repo, audit);
    UUID actor = UUID.randomUUID();

    assertThatThrownBy(() -> create.execute(command("T".repeat(51)), actor))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repo, never()).save(any());
    verifyNoInteractions(audit);
  }

  @Test
  void blankTaxCodeBecomesNull() {
    var repo = mock(OrganizationRepository.class);
    var audit = mock(AuditWriter.class);

    var result =
        new CreateOrganizationUseCase(repo, audit).execute(command("   "), UUID.randomUUID());

    assertThat(result.taxCode()).isNull();
  }
}
