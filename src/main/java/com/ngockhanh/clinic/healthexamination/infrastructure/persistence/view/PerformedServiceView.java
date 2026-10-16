package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.util.UUID;

/** SQL projection linking a Participant to one agreement service recorded as performed. */
public record PerformedServiceView(UUID participantId, UUID agreementServiceId) {}
