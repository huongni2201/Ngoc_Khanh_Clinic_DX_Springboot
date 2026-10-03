package com.ngockhanh.clinic.identity.application.query;

public record GetSessionQuery(UserPrincipal principal) {
  @Override
  public String toString() {
    return "GetSessionQuery[userId=" + (principal == null ? null : principal.userId()) + "]";
  }
}
