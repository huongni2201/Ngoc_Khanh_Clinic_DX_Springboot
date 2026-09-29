package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.GetStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import org.springframework.stereotype.Service;

@Service
public class GetStaffSessionUseCase {
  public StaffSessionResponse execute(GetStaffSessionQuery query) {
    if (query.principal() == null) {
      throw AuthenticationFailure.invalid();
    }
    return StaffSessionResponse.from(query.principal());
  }
}
