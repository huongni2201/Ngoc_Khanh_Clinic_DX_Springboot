package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;

public record LogoutStaffSessionCommand(List<String> sessionIds, UUID correlationId) {
    public LogoutStaffSessionCommand {
        sessionIds = List.copyOf(sessionIds);
    }

    @Override
    public String toString() {
        return "LogoutStaffSessionCommand[correlationId=" + correlationId + "]";
    }
}
