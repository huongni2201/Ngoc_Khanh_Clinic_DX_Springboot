package com.ngockhanh.clinic.integration.application.imports;

import java.util.UUID;

/** Outcome of reserving the request key of an examination detail import. */
public sealed interface ServiceReconciliationReservation {
  /** The key is new: process the request and complete the reservation. */
  record New(UUID reservationId) implements ServiceReconciliationReservation {
    public New {
      if (reservationId == null) throw new IllegalArgumentException("Reservation ID is required");
    }
  }

  /** The same request already completed: return its stored receipt and change nothing. */
  record Replay(ServiceReconciliationReceipt receipt) implements ServiceReconciliationReservation {
    public Replay {
      if (receipt == null) throw new IllegalArgumentException("Receipt is required");
    }
  }
}
