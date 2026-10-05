package com.ngockhanh.clinic.identity.application.response;

import lombok.Builder;

@Builder
public record CsrfResponse(String token, String headerName) {
  @Override
  public String toString() {
    return "CsrfResponse[headerName=" + headerName + "]";
  }

  public static class CsrfResponseBuilder {
    @Override
    public String toString() {
      return "CsrfResponseBuilder[redacted]";
    }
  }
}
