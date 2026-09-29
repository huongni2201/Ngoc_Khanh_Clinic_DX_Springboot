package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.command.LogoutStaffSessionCommand;
import com.ngockhanh.clinic.identity.application.port.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LogoutStaffSessionUseCase {
  private final SessionStore sessions;
  private final AccountTransactions accounts;
  private final Clock clock;

  public void execute(LogoutStaffSessionCommand command) {
    String sessionId = StaffSessionSupport.singleCookie(command.sessionIds(), false);
    var stored = sessionId == null ? null
        : StaffSessionSupport.sessionDependency(() -> sessions.find(sessionId));
    if (sessionId != null) {
      StaffSessionSupport.sessionDependency(() -> sessions.delete(sessionId));
    }
    if (stored != null) {
      StaffSessionSupport.recordRevocation(accounts, clock, stored.userId(), false, command.correlationId());
    }
  }
}
