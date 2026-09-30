package com.ngockhanh.clinic.identity.application.port;

import java.util.UUID;

@org.springframework.modulith.NamedInterface("sessions")
public interface SessionRevocation {
    void revokeAllSessions(UUID userId);
}
