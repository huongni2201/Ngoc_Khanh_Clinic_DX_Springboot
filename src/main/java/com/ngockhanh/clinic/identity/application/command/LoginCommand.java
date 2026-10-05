package com.ngockhanh.clinic.identity.application.command;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record LoginCommand(
    String username,
    String password,
    String clientIp,
    List<String> sessionIds,
    UUID correlationId) {
  public LoginCommand {
    sessionIds = List.copyOf(sessionIds);
  }

  @Override
  public String toString() {
    return "LoginCommand[correlationId=" + correlationId + "]";
  }

  public static class LoginCommandBuilder {
    @Override
    public String toString() {
      return "LoginCommandBuilder[redacted]";
    }
  }
}
