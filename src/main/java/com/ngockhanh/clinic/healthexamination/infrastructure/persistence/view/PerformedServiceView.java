package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.util.UUID;

/** SQL projection linking a Participant to one batch service recorded as performed. */
public record PerformedServiceView(UUID batchParticipantId, UUID batchServiceId) {}
