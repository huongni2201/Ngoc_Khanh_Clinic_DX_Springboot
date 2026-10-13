package com.ngockhanh.clinic.accesscontrol.application.response;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Staff member or patient that can own a new account. Staff owners carry a department and an email;
 * patient owners carry a date of birth. The other fields are null.
 *
 * @param ownerId staff member or patient identifier
 * @param ownerCode staff code or patient code
 * @param fullName full name
 * @param departmentName primary department of a staff member, or null
 * @param dateOfBirth date of birth of a patient, or null
 * @param phone phone number as stored, or null
 * @param email email of a staff member, or null
 * @param smsPhoneNumber normalized mobile number that can receive the SMS, or null when the stored
 *     phone number cannot
 */
@Builder
public record EligibleOwnerResponse(
    UUID ownerId,
    String ownerCode,
    String fullName,
    String departmentName,
    LocalDate dateOfBirth,
    String phone,
    String email,
    String smsPhoneNumber) {}
