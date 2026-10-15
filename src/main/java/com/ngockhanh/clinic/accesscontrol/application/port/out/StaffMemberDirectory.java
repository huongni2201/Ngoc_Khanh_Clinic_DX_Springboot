package com.ngockhanh.clinic.accesscontrol.application.port.out;

import com.ngockhanh.clinic.accesscontrol.application.query.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;

/** Read model of staff administration: staff members with their accounts, and grantable roles. */
public interface StaffMemberDirectory {
  /**
   * Returns one page of staff members of every status.
   *
   * @param page one-based page
   * @param size page size
   * @param searchKey contains match on staff code, full name and username, or null for all
   * @param sortKey already allowlisted: {@code id}, {@code staffCode} or {@code fullName}
   * @param sortBy {@code ASC} or {@code DESC}
   * @return the page; items are empty past the last page
   */
  PageResponse<StaffMemberSummary> search(
      Integer page, Integer size, String searchKey, String sortKey, String sortBy);

  /**
   * Lists the active roles that may be granted to a staff account, ordered by code.
   *
   * @return roles other than {@code PATIENT}
   */
  List<AssignableRole> findAssignableRoles();
}
