package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GetCsrfTokenUseCase {
    public CsrfResponse execute(GetCsrfTokenQuery query) {
        log.debug("Preparing CSRF token response");
        return new CsrfResponse(query.token(), query.headerName());
    }
}
