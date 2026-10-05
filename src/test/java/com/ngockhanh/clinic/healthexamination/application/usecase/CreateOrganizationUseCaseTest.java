package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditEventMapper;
import com.ngockhanh.clinic.audit.infrastructure.persistence.record.AuditEventRecord;
import com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class CreateOrganizationUseCaseTest {
  private CreateOrganizationCommand command(String code, String taxCode) {
    return CreateOrganizationCommand.builder()
        .code(code)
        .name("School")
        .organizationType("SCHOOL")
        .taxCode(taxCode)
        .phone("0901")
        .email("school@example.test")
        .address("Address")
        .contactFullName("Contact")
        .contactPosition(null)
        .contactPhone("0902")
        .contactEmail("contact@example.test")
        .build();
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
  void sharedWriterKeepsBusinessSnapshotsAndDatabaseAssignedTime() {
    var repo = mock(OrganizationRepository.class);
    var mapper = mock(AuditEventMapper.class);
    when(mapper.insert(any())).thenReturn(1);
    var json = JsonMapper.builder().build();
    var writer = new MyBatisAuditWriter(mapper, json);
    UUID actor = UUID.randomUUID();

    var result = new CreateOrganizationUseCase(repo, writer).execute(command("S1", null), actor);

    var event = ArgumentCaptor.forClass(AuditEventRecord.class);
    verify(mapper).insert(event.capture());
    var recorded = event.getValue();
    assertThat(recorded.actorAccountId()).isEqualTo(actor);
    assertThat(recorded.action()).isEqualTo("CREATE_ORGANIZATION");
    assertThat(recorded.resourceType()).isEqualTo("ORGANIZATION");
    assertThat(recorded.resourceId()).isEqualTo(result.id());
    assertThat(recorded.occurredAt()).isNull();
    assertThat(recorded.correlationId()).isNull();
    assertThat(recorded.departmentId()).isNull();
    assertThat(recorded.id().version()).isEqualTo(7);
    var metadata = json.readTree(recorded.metadata());
    assertThat(metadata.get("before").isEmpty()).isTrue();
    assertThat(metadata.get("after").get("code").asString()).isEqualTo("S1");
    assertThat(metadata.get("after").size()).isEqualTo(1);
  }

  @Test
  void sharedWriterPropagatesBusinessAuditInsertFailure() {
    var repo = mock(OrganizationRepository.class);
    var mapper = mock(AuditEventMapper.class);
    when(mapper.insert(any())).thenReturn(0);
    var writer = new MyBatisAuditWriter(mapper, JsonMapper.builder().build());

    assertThatThrownBy(
            () ->
                new CreateOrganizationUseCase(repo, writer)
                    .execute(command("S1", null), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Audit not saved");
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
