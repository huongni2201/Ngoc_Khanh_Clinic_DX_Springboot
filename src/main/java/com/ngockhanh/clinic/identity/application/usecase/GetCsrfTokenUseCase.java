package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetCsrfTokenUseCase {
  public CsrfResponse execute(GetCsrfTokenQuery query) {
    return new CsrfResponse(query.token(), query.headerName());
  }
}
