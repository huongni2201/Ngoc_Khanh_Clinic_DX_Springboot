package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.GetStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GetStaffSessionUseCase {
    public StaffSessionResponse execute(GetStaffSessionQuery query) {
        if (query.principal() == null) {
            throw AuthenticationFailure.invalid();
        }
        log.debug("Reading staff session response userId={}", query.principal().userId());
        return StaffSessionResponse.from(query.principal());
    }
}
