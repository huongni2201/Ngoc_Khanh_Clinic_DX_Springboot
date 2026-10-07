package com.ngockhanh.clinic.integration.application.imports;

import java.util.UUID;

/** Outcome of reserving an import request key. */
public sealed interface ImportReservation {
  /** The key is new: process the request and complete the reservation. */
  record New(UUID reservationId) implements ImportReservation {
    public New {
      if (reservationId == null) throw new IllegalArgumentException("Reservation ID is required");
    }
  }

  /** The same request already completed: return its stored receipt and change nothing. */
  record Replay(ImportReceipt receipt) implements ImportReservation {
    public Replay {
      if (receipt == null) throw new IllegalArgumentException("Receipt is required");
    }
  }
}
