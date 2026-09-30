package com.ngockhanh.clinic.identity.application.response;

public record LoginResult(String sessionId, UserSessionResponse response) {
    @Override
    public String toString() {
        return "LoginResult[userId=" + response.userId() + "]";
    }
}
