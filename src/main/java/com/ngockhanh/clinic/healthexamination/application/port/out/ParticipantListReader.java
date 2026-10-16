package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantPage;
import java.util.Optional;
import java.util.UUID;

/** Read port of the Participant list; it reads projections and never restores aggregates. */
public interface ParticipantListReader {
  /**
   * Reads one page of the Participants of an agreement.
   *
   * @return the page, or empty when the agreement does not belong to the organization or is deleted
   */
  Optional<ParticipantPage> readPage(
      UUID organizationId, UUID agreementId, ParticipantListCriteria criteria);
}
