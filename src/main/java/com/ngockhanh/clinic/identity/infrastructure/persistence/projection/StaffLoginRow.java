package com.ngockhanh.clinic.identity.infrastructure.persistence.projection;

import java.util.UUID;

public record StaffLoginRow(UUID userId, UUID staffId, String username, String password, String status,
                            String principalType, boolean staffActive) {
    @Override
    public String toString() {
        return "StaffLoginRow[userId=" + userId + "]";
    }
}
