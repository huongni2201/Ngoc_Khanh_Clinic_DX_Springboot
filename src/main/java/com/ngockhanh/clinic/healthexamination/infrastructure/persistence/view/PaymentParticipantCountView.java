package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

/** SQL projection of the Participant counters of the payment summary scope. */
public record PaymentParticipantCountView(long registered, long attended, long reconciled) {}
