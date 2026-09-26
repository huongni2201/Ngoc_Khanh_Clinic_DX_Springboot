package com.ngockhanh.clinic.shared.web;

public record ApiResponse<T>(String result, int code, String message, T data) {

    public ApiResponse {
        result = "OK";
    }

    public ApiResponse(int code, String message) {
        this("OK", code, message, null);
    }

    public ApiResponse(int code, String message, T data) {
        this("OK", code, message, data);
    }
}
