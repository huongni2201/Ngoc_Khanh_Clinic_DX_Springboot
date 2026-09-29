package com.ngockhanh.clinic.identity.application.query;

public record GetCsrfTokenQuery(String token, String headerName) {
  @Override
  public String toString() {
    return "GetCsrfTokenQuery[headerName=" + headerName + "]";
  }
}
