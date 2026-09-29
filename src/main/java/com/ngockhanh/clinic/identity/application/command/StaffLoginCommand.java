package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;

public record StaffLoginCommand(String username, String password, String clientIp,
                                List<String> sessionIds, UUID correlationId) {
  public StaffLoginCommand {
    sessionIds = List.copyOf(sessionIds);
  }

  @Override
  public String toString() {
    return "StaffLoginCommand[correlationId=" + correlationId + "]";
  }
}
