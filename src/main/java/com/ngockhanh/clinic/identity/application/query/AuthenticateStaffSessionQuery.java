package com.ngockhanh.clinic.identity.application.query;

import java.util.List;

public record AuthenticateStaffSessionQuery(List<String> sessionIds) {
  public AuthenticateStaffSessionQuery {
    sessionIds = List.copyOf(sessionIds);
  }

  @Override
  public String toString() {
    return "AuthenticateStaffSessionQuery[sessionCount=" + sessionIds.size() + "]";
  }
}
