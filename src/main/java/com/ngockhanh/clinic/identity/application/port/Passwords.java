package com.ngockhanh.clinic.identity.application.port;

public interface Passwords {
    boolean matches(String password, String encoded);
}
