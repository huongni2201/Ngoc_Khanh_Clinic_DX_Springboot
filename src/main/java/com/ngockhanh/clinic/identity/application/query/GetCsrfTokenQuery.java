package com.ngockhanh.clinic.identity.application.query;

import lombok.Builder;

@Builder
public record GetCsrfTokenQuery(String token, String headerName) {
  @Override
  public String toString() {
    return "GetCsrfTokenQuery[headerName=" + headerName + "]";
  }

  public static class GetCsrfTokenQueryBuilder {
    @Override
    public String toString() {
      return "GetCsrfTokenQueryBuilder[redacted]";
    }
  }
}
