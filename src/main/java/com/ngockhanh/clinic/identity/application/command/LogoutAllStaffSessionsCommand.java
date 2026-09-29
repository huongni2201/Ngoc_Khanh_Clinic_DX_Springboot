package com.ngockhanh.clinic.identity.application.command;

import java.util.UUID;

public record LogoutAllStaffSessionsCommand(UUID userId, UUID correlationId) {
    @Override
    public String toString() {
        return "LogoutAllStaffSessionsCommand[userId=" + userId + ", correlationId=" + correlationId + "]";
    }
}
