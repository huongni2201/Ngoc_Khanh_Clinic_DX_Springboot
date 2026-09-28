package com.ngockhanh.clinic.identity.application.port;

public interface LoginThrottle {
  void check(String username, String ip);

  void failed(String username);
}
