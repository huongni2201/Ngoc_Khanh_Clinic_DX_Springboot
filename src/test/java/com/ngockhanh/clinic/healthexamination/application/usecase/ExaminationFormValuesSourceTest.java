package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormSourceLoader;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationFormValuesSource;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What a background print job reads for many Participants, and how it leaves some out. */
class ExaminationFormValuesSourceTest extends ManualParticipantUseCaseTestBase {
  private final ExaminationFormSourceLoader loader =
      new ExaminationFormSourceLoader(
          support,
          organizations,
          participants,
          sessions,
          assignments,
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final ExaminationFormValuesSource source = new ExaminationFormValuesSource(loader);

  ExaminationFormValuesSourceTest() {
    super("PARTICIPANT_EXAMINATION_FORM_BULK_PRINT");
  }

  private Map<String, String> context() {
    return Map.of(
        ExaminationFormValuesSource.ORGANIZATION_ID, organizationId.toString(),
        ExaminationFormValuesSource.AGREEMENT_ID, agreementId.toString());
  }

  @Test
  void loadsActiveParticipantsInTheRequestedOrderAndLeavesOutTheOthers() {
    givenOpenAgreement();
    var first = stored(0);
    var cancelled = stored(1, progress(null, RosterStatus.CANCELLED, null));
    var second = stored(0);
    var missing = UUID.randomUUID();
    when(participants.findManyInAgreement(any(), any()))
        .thenReturn(List.of(second, first, cancelled));
    var ids =
        List.of(second.getId().value(), missing, cancelled.getId().value(), first.getId().value());

    var loaded = source.load(context(), ids);

    assertThat(loaded.items())
        .extracting(DocumentValuesSource.Item::id)
        .containsExactly(second.getId().value(), first.getId().value());
    assertThat(loaded.items().get(0).values())
        .containsEntry("participant.code", "NV-001")
        .containsEntry("participant.fullName", FULL_NAME)
        .containsEntry("organization.name", "Clinic Partner")
        .containsEntry("printedDate", "04/10/2026");
    assertThat(loaded.skipped())
        .containsExactly(
            new DocumentValuesSource.Skipped(missing, ExaminationFormSourceLoader.NOT_FOUND),
            new DocumentValuesSource.Skipped(
                cancelled.getId().value(), ExaminationFormSourceLoader.CANCELLED));
  }

  @Test
  void readsWithoutLockingAnyRow() {
    givenOpenAgreement();
    var participant = stored(0);
    when(participants.findManyInAgreement(any(), any())).thenReturn(List.of(participant));

    source.load(context(), List.of(participant.getId().value()));

    verify(participants, never()).findManyInAgreementForUpdate(any(), any());
  }

  @Test
  void anAgreementThatIsNoLongerPrintableStopsTheJobInsteadOfSkippingEveryone() {
    for (var status : AgreementStatus.values()) {
      if (status == AgreementStatus.ACTIVE || status == AgreementStatus.ENDED) continue;
      givenAgreement(status, null, true);
      assertThatThrownBy(() -> source.load(context(), List.of(UUID.randomUUID())))
          .as(status.name())
          .isInstanceOf(ConflictException.class);
    }
  }

  @Test
  void anAgreementOrOrganizationThatIsGoneStopsTheJob() {
    when(agreements.findDetails(organizationId, agreementId, false)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> source.load(context(), List.of(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);

    givenOpenAgreement();
    when(organizations.findById(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> source.load(context(), List.of(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
