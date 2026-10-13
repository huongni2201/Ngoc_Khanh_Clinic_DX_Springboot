package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory.EligiblePatientOwner;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.EligibleOwnerListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.EligibleOwnerResponse;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository.EligibleStaffOwner;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SmsPhoneNumber;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the active staff members or patients without a user account that can own a new account
 * (UC-005). The result is bounded instead of paginated: patients that already have an account are
 * filtered after the search, so an exact total is not available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchEligibleOwnersUseCase {
  /** Permission required to search eligible owners (ADR-0015, V005). */
  public static final String PERMISSION = "USER_ACCOUNT_OWNER_SEARCH";

  /** Maximum number of eligible owners returned. */
  static final Integer LIMIT = 20;

  /** Patients read before removing those that already have an account. */
  static final Integer PATIENT_OVERFETCH = 100;

  private final StaffMemberRepository staffMembers;
  private final PatientAccountOwnerDirectory patients;
  private final UserAccountRepository accounts;

  /**
   * Searches eligible owners: active profiles without an account. Staff members match on staff
   * code, full name or phone number; patients on patient code, full name, phone number or the exact
   * identification number.
   *
   * @param accountType STAFF or PATIENT
   * @param keyword search text
   * @param principal authenticated Administrator
   * @return at most 20 eligible owners ordered by full name, and whether the keyword should be
   *     refined
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     ACCESS_DENIED} without the owner search permission
   * @throws IllegalArgumentException if the account type is unknown or the keyword is blank
   */
  @Transactional(readOnly = true)
  public EligibleOwnerListResponse execute(
      String accountType, String keyword, UserPrincipal principal) {
    requirePermission(principal);
    AccountType type =
        AccountType.from(accountType)
            .orElseThrow(() -> new IllegalArgumentException("Unknown account type"));
    if (keyword == null || keyword.isBlank())
      throw new IllegalArgumentException("Keyword is required");
    String text = keyword.strip();
    EligibleOwnerListResponse result = type == AccountType.STAFF ? staff(text) : patients(text);
    log.debug(
        "Eligible owners searched: accountType={}, resultCount={}, limited={}",
        type,
        result.items().size(),
        result.limited());
    return result;
  }

  private EligibleOwnerListResponse staff(String keyword) {
    List<EligibleStaffOwner> rows = staffMembers.searchActiveWithoutAccount(keyword, LIMIT + 1);
    return new EligibleOwnerListResponse(
        rows.stream().limit(LIMIT).map(SearchEligibleOwnersUseCase::toResponse).toList(),
        rows.size() > LIMIT);
  }

  private EligibleOwnerListResponse patients(String keyword) {
    List<EligiblePatientOwner> rows = patients.searchActive(keyword, PATIENT_OVERFETCH);
    Set<UUID> linked =
        rows.isEmpty()
            ? Set.of()
            : accounts.findLinkedPatientIds(rows.stream().map(EligiblePatientOwner::id).toList());
    List<EligiblePatientOwner> free =
        rows.stream().filter(row -> !linked.contains(row.id())).toList();
    return new EligibleOwnerListResponse(
        free.stream().limit(LIMIT).map(SearchEligibleOwnersUseCase::toResponse).toList(),
        free.size() > LIMIT || rows.size() == PATIENT_OVERFETCH);
  }

  private static EligibleOwnerResponse toResponse(EligibleStaffOwner row) {
    return EligibleOwnerResponse.builder()
        .ownerId(row.id())
        .ownerCode(row.staffCode())
        .fullName(row.fullName())
        .departmentName(row.departmentName())
        .phone(row.phone())
        .email(row.email())
        .smsPhoneNumber(smsNumber(row.phone()))
        .build();
  }

  private static EligibleOwnerResponse toResponse(EligiblePatientOwner row) {
    return EligibleOwnerResponse.builder()
        .ownerId(row.id())
        .ownerCode(row.patientCode())
        .fullName(row.fullName())
        .dateOfBirth(row.dateOfBirth())
        .phone(row.phone())
        .smsPhoneNumber(smsNumber(row.phone()))
        .build();
  }

  private static String smsNumber(String phone) {
    return SmsPhoneNumber.parse(phone).map(SmsPhoneNumber::value).orElse(null);
  }

  /**
   * Authorizes every supported caller, not only HTTP: an actor ID alone proves nothing, the
   * principal must be a STAFF account whose roles carry {@link #PERMISSION}.
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
