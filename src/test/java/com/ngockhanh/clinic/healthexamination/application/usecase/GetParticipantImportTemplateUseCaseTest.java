package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.port.ParticipantTemplateWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GetParticipantImportTemplateUseCaseTest {
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches =
      mock(HealthExaminationBatchRepository.class);
  private final ParticipantTemplateWriter writer = mock(ParticipantTemplateWriter.class);
  private final GetParticipantImportTemplateUseCase useCase =
      new GetParticipantImportTemplateUseCase(
          new ParticipantAccessPolicy(), organizations, batches, writer);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();

  @BeforeEach
  void anActiveOrganizationWithAReadyBatchAtVersionThree() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(batches.findDetails(organizationId, batchId, false))
        .thenReturn(
            Optional.of(
                details(batch(organizationId, batchId, BatchStatus.READY, 3, null, UUID.randomUUID()))));
    when(writer.write(any())).thenReturn(new byte[] {1, 2, 3});
  }

  @Test
  void rendersTheTemplateWithTheBatchVersionAndSortedDatesWithoutWritingAnything() {
    var template = useCase.execute(organizationId, batchId, staff(TEMPLATE_DOWNLOAD));

    var data = ArgumentCaptor.forClass(ParticipantTemplateData.class);
    verify(writer).write(data.capture());
    assertThat(data.getValue().batchId()).isEqualTo(batchId);
    assertThat(data.getValue().batchRowVersion()).isEqualTo(3);
    assertThat(data.getValue().examinationDates()).containsExactly(FIRST_DAY, SECOND_DAY);
    assertThat(template.content()).containsExactly(1, 2, 3);
    assertThat(template.fileName()).isEqualTo("participant-import-template.xlsx");
  }

  @Test
  void requiresTheReadPermissionNotTheImportPermission() {
    for (var principal : List.of(staff(), staff(READ), staff(IMPORT), patient()))
      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    verifyNoInteractions(organizations, batches, writer);
  }

  @Test
  void reportsAMissingOrDeletedBatchAsNotFound() {
    when(batches.findDetails(organizationId, batchId, false)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, staff(TEMPLATE_DOWNLOAD)))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(writer);
  }

  @Test
  void refusesABatchThatDoesNotAcceptImports() {
    for (var status : List.of(BatchStatus.FINALIZED, BatchStatus.CLOSED)) {
      when(batches.findDetails(organizationId, batchId, false))
          .thenReturn(
              Optional.of(details(batch(organizationId, batchId, status, 3, null, UUID.randomUUID()))));
      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, staff(TEMPLATE_DOWNLOAD)))
          .isInstanceOf(ConflictException.class)
          .hasMessage("Batch does not accept Participant imports");
    }
    verifyNoInteractions(writer);
  }

  @Test
  void refusesAnInactiveOrganization() {
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(inactiveOrganization(organizationId)));

    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, staff(TEMPLATE_DOWNLOAD)))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(writer);
  }
}
