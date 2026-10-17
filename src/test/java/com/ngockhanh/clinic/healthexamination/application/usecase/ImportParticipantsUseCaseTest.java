package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.command.ImportParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantExcelReader;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantImportCommitter;
import com.ngockhanh.clinic.healthexamination.application.service.SessionAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Authorization of the import, in particular the extra permission scheduling needs. */
class ImportParticipantsUseCaseTest {
  private final ParticipantExcelReader reader = mock(ParticipantExcelReader.class);
  private final ParticipantImportCommitter committer = mock(ParticipantImportCommitter.class);
  private final ImportParticipantsUseCase useCase =
      new ImportParticipantsUseCase(
          new ParticipantAccessPolicy(), new SessionAccessPolicy(), reader, committer);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();

  private ImportParticipantsCommand command(UUID sessionId) {
    return new ImportParticipantsCommand(new byte[] {1}, 1L, UUID.randomUUID(), sessionId);
  }

  private static void assertDenied(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
  }

  @Test
  void schedulingAtImportNeedsTheAssignmentCreatePermissionToo() {
    assertDenied(
        () ->
            useCase.execute(
                organizationId, agreementId, command(UUID.randomUUID()), staff(IMPORT)));
    verifyNoInteractions(reader, committer);
  }

  @Test
  void anImportWithoutASessionNeedsOnlyTheImportPermission() {
    when(reader.read(any()))
        .thenThrow(
            new ApplicationException(ApplicationException.Type.INVALID_INPUT, "unreadable"));

    assertThatThrownBy(
            () -> useCase.execute(organizationId, agreementId, command(null), staff(IMPORT)))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            reached ->
                assertThat(reached.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verify(reader).read(any());
  }

  @Test
  void aCallerWithBothPermissionsGetsPastTheAccessChecks() {
    when(reader.read(any()))
        .thenThrow(
            new ApplicationException(ApplicationException.Type.INVALID_INPUT, "unreadable"));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId,
                    agreementId,
                    command(UUID.randomUUID()),
                    staff(IMPORT, SessionAccessPolicy.ASSIGNMENT_CREATE)))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            reached ->
                assertThat(reached.type()).isEqualTo(ApplicationException.Type.INVALID_INPUT));
    verify(reader).read(any());
  }

  @Test
  void theImportPermissionIsStillRequiredWhenASessionIsNamed() {
    assertDenied(
        () ->
            useCase.execute(
                organizationId,
                agreementId,
                command(UUID.randomUUID()),
                staff(SessionAccessPolicy.ASSIGNMENT_CREATE)));
    verifyNoInteractions(reader, committer);
  }
}
