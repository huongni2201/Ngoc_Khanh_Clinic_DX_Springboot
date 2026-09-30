package com.ngockhanh.clinic.identity.application.response;

public record StaffLoginResult(String sessionId, StaffSessionResponse response) {
    @Override
    public String toString() {
        return "StaffLoginResult[userId=" + response.userId() + "]";
    }
}
