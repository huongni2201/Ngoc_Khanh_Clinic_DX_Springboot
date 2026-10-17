package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.UUID;

/**
 * What the template of one agreement carries: the agreement it belongs to and the version it was
 * issued for.
 */
public record ParticipantTemplateData(UUID agreementId, long agreementRowVersion) {}
