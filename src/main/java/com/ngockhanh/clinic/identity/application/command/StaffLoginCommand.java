package com.ngockhanh.clinic.identity.application.command;

import java.util.UUID;

public record StaffLoginCommand(String username, String password, String clientIp,
                                String previousSessionId, UUID correlationId) {
  @Override
  public String toString() {
    return "StaffLoginCommand[correlationId=" + correlationId + "]";
  }
}
