package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.command.LogoutAllStaffSessionsCommand;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import com.ngockhanh.clinic.identity.application.port.access.SessionRevocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutAllStaffSessionsUseCase implements SessionRevocation {
  private final SessionStore sessions;
  private final AccountTransactions accounts;
  private final Clock clock;

  public void execute(LogoutAllStaffSessionsCommand command) {
    StaffSessionSupport.sessionDependency(() -> sessions.revokeAll(command.userId()));
    StaffSessionSupport.recordRevocation(accounts, clock, command.userId(), true, command.correlationId());
  }

  @Override
  public void revokeAllSessions(UUID userId) {
    execute(new LogoutAllStaffSessionsCommand(userId, UUID.randomUUID()));
  }
}
