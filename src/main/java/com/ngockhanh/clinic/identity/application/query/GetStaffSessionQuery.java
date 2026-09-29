package com.ngockhanh.clinic.identity.application.query;

import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;

public record GetStaffSessionQuery(StaffPrincipal principal) {
  @Override
  public String toString() {
    return "GetStaffSessionQuery[userId=" + (principal == null ? null : principal.userId()) + "]";
  }
}
