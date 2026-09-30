package com.ngockhanh.clinic.identity.application.query;

import java.util.List;

public record AuthenticateSessionQuery(List<String> sessionIds) {
    public AuthenticateSessionQuery {
        sessionIds = List.copyOf(sessionIds);
    }

    @Override
    public String toString() {
        return "AuthenticateSessionQuery[sessionCount=" + sessionIds.size() + "]";
    }
}
