package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.util.UUID;

/** SQL projection of the scheduling facts of a Participant and the status of its agreement. */
public record ParticipantScopeView(
    UUID id, UUID agreementId, String rosterStatus, String agreementStatus) {}
