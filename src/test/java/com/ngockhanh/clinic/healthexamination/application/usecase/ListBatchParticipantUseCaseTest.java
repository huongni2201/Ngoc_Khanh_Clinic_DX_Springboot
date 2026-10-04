package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.RosterFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.*;
import org.junit.jupiter.api.Test;

class ListBatchParticipantUseCaseTest {
  @Test
  void scopesTheRosterAndEscapesSearchWildcards() {
    var batches = mock(HealthExaminationBatchRepository.class);
    var participants = mock(HealthExaminationBatchParticipantRepository.class);
    when(batches.findByIdAndOrganizationId(id(1), id(2))).thenReturn(Optional.of(batch()));
    var usecase = new ListBatchParticipantUseCase(batches, participants);
    var result =
        usecase.execute(
            id(2).value(),
            id(1).value(),
            new OrganizationBatchParticipantQuery(2, 10, "%_", "DESC", "fullName"));
    assertThat(result.items()).isEmpty();
    verify(participants).findByBatch(id(1), 10, 10, "%\\%\\_%", "fullName", "DESC");
    assertThatThrownBy(
            () ->
                usecase.execute(
                    id(9).value(),
                    id(1).value(),
                    new OrganizationBatchParticipantQuery(1, 10, null, null, null)))
        .isInstanceOf(ResourceNotFoundException.class);
  }
}
