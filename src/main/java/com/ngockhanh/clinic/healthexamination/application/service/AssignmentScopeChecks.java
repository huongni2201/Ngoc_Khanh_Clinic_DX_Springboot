package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository.ParticipantScope;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks shared by the assignment use cases: the organization may take changes, and a Participant
 * may be scheduled into a session. It holds no state and starts no transaction.
 */
@Component
@RequiredArgsConstructor
public class AssignmentScopeChecks {
  private final OrganizationRepository organizations;

  /**
   * Requires an existing, active organization.
   *
   * @throws ResourceNotFoundException when the organization does not exist
   * @throws DomainRuleViolation when it is inactive
   */
  public void requireActiveOrganization(java.util.UUID organizationId) {
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE)
      throw new DomainRuleViolation("Organization is not active");
  }

  /**
   * Requires that the Participant can be scheduled or have attendance recorded: it is on the active
   * roster of an agreement that still accepts changes, and the session serves that agreement.
   *
   * @throws ConflictException when the Participant is cancelled or its agreement does not accept
   *     changes
   * @throws DomainRuleViolation when the session belongs to another agreement
   */
  public void requireSchedulable(ParticipantScope participant, HealthExaminationSession session) {
    if (participant.rosterStatus() != RosterStatus.ACTIVE)
      throw new ConflictException("A cancelled Participant cannot be scheduled");
    if (participant.agreementStatus() != AgreementStatus.DRAFT
        && participant.agreementStatus() != AgreementStatus.ACTIVE)
      throw new ConflictException("The agreement of the Participant does not accept changes");
    session.requireServes(participant.agreementId());
  }
}
