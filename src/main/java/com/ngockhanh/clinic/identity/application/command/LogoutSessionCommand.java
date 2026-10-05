package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record LogoutSessionCommand(List<String> sessionIds, UUID correlationId) {
  public LogoutSessionCommand {
    sessionIds = List.copyOf(sessionIds);
  }

  @Override
  public String toString() {
    return "LogoutSessionCommand[correlationId=" + correlationId + "]";
  }

  public static class LogoutSessionCommandBuilder {
    @Override
    public String toString() {
      return "LogoutSessionCommandBuilder[redacted]";
    }
  }
}
