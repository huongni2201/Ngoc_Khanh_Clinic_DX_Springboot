package com.ngockhanh.clinic.accesscontrol.application.response;

/**
 * Whether a username can be used by a new account.
 *
 * @param available true when no account uses the username
 */
public record UsernameAvailabilityResponse(boolean available) {}
