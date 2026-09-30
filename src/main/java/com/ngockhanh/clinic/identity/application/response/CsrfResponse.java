package com.ngockhanh.clinic.identity.application.response;

public record CsrfResponse(String token, String headerName) {
    @Override
    public String toString() {
        return "CsrfResponse[headerName=" + headerName + "]";
    }
}
