package com.ngockhanh.clinic.identity.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 200) String username,
                                @NotEmpty @Size(max = 72) String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
