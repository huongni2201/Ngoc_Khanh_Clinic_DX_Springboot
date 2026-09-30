package com.ngockhanh.clinic.identity.application.command;

import java.util.UUID;

public record LogoutAllSessionsCommand(UUID userId, UUID correlationId) {
    @Override
    public String toString() {
        return "LogoutAllSessionsCommand[userId=" + userId + ", correlationId=" + correlationId + "]";
    }
}
