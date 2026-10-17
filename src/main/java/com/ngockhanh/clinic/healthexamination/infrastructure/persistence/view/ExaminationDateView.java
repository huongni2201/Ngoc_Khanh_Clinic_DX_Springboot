package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of the examination date printed on the form of one Participant. */
public record ExaminationDateView(UUID participantId, LocalDate examinationDate) {}
