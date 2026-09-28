package com.ngockhanh.clinic.identity.application.port.access;

import java.util.UUID;

public interface SessionRevocation {
  void revokeAllSessions(UUID userId);
}
