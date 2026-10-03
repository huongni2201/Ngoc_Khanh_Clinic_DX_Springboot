package com.ngockhanh.clinic.identity.application.usecase;

import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.GetSessionQuery;
import com.ngockhanh.clinic.identity.application.response.UserSessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GetSessionUseCase {
  public UserSessionResponse execute(GetSessionQuery query) {
    if (query.principal() == null) {
      throw AuthenticationFailure.invalid();
    }
    log.debug("Reading user session response userId={}", query.principal().userId());
    return UserSessionResponse.from(query.principal());
  }
}
