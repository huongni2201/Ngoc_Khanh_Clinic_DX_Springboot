package com.ngockhanh.clinic.healthexamination.domain.enums;

public enum BatchStatus {
  DRAFT,
  READY,
  FINALIZED,
  CLOSED;

  public boolean allowsRosterImport() {
    return this == DRAFT || this == READY;
  }
}
