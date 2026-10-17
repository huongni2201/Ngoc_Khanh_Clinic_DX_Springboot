package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

/** SQL projection of the session counters of the payment summary scope. */
public record PaymentSessionCountView(long sessions, long visits) {}
