package com.ngockhanh.clinic.shared.web;

public record ApiError(
        String result,
        int code,
        String message
) {
    public ApiError {
        result = "NG";
    }

    public ApiError(int code, String message) {
        this("NG", code, message);
    }
}
