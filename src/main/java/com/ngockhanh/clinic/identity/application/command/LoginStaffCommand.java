package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;

public record LoginStaffCommand(String username, String password, String clientIp,
                                List<String> sessionIds, UUID correlationId) {
    public LoginStaffCommand {
        sessionIds = List.copyOf(sessionIds);
    }

    @Override
    public String toString() {
        return "LoginStaffCommand[correlationId=" + correlationId + "]";
    }
}
