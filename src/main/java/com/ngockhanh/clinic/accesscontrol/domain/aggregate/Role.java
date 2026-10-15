package com.ngockhanh.clinic.accesscontrol.domain.aggregate;

import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Role that groups permissions for access control (SRS UC-008). The code is fixed at creation; the
 * name, description and active flag may change. An inactive role stays assigned to its accounts but
 * grants no permission (BR-06).
 *
 * <p>The {@code ADMINISTRATOR} role must stay active and keep the permissions that administer
 * roles, so that roles can always be administered again (owner decision 2026-10-15, not in the SRS
 * yet).
 */
public final class Role {
  public static final String ADMINISTRATOR_CODE = "ADMINISTRATOR";
  public static final Integer CODE_MAX_LENGTH = 80;
  public static final Integer NAME_MAX_LENGTH = 100;
  public static final Integer DESCRIPTION_MAX_LENGTH = 500;

  /** Permissions the {@code ADMINISTRATOR} role never loses. */
  public static final Set<String> ADMINISTRATOR_PROTECTED_PERMISSIONS =
      Set.of(
          "ROLE_MANAGE",
          "ROLE_UPDATE",
          "PERMISSION_VIEW",
          "ROLE_PERMISSION_VIEW",
          "ROLE_PERMISSION_ASSIGN");

  /** Uppercase letters, digits and underscores, starting with a letter (SRS 1.3.2). */
  private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

  private final UUID id;
  private final String code;
  private final Long rowVersion;
  private String name;
  private String description;
  private Boolean active;

  private Role(
      UUID id, String code, String name, String description, Boolean active, Long rowVersion) {
    if (id == null) throw new IllegalArgumentException("Role id is required");
    if (code == null || code.length() > CODE_MAX_LENGTH || !CODE_FORMAT.matcher(code).matches())
      throw new IllegalArgumentException("Invalid role code");
    if (rowVersion == null || rowVersion < 0)
      throw new IllegalArgumentException("Row version is required");
    this.id = id;
    this.code = code;
    this.rowVersion = rowVersion;
    applyDetails(name, description, active);
  }

  /**
   * Creates a role that has not been stored yet.
   *
   * @throws IllegalArgumentException when the code format, a length or a required value is invalid
   */
  public static Role create(UUID id, String code, String name, String description, Boolean active) {
    return new Role(id, code, name, description, active, 0L);
  }

  /** Restores a stored role. */
  public static Role restore(
      UUID id, String code, String name, String description, Boolean active, Long rowVersion) {
    return new Role(id, code, name, description, active, rowVersion);
  }

  /**
   * Replaces the name, description and active flag. The code never changes.
   *
   * @throws IllegalArgumentException when a length or a required value is invalid
   * @throws ProtectedRoleException when the {@code ADMINISTRATOR} role would become inactive
   */
  public void update(String name, String description, Boolean active) {
    if (isAdministrator() && Boolean.FALSE.equals(active))
      throw new ProtectedRoleException("The Administrator role cannot be deactivated");
    applyDetails(name, description, active);
  }

  /**
   * Rejects removing a permission that the {@code ADMINISTRATOR} role must keep.
   *
   * @param permissionCodes permissions about to be removed from this role
   * @throws ProtectedRoleException when one of them is protected for this role
   */
  public void requireRevocable(Collection<String> permissionCodes) {
    if (isAdministrator()
        && permissionCodes.stream().anyMatch(ADMINISTRATOR_PROTECTED_PERMISSIONS::contains))
      throw new ProtectedRoleException(
          "The Administrator role must keep the role administration permissions");
  }

  public Boolean isAdministrator() {
    return ADMINISTRATOR_CODE.equals(code);
  }

  private void applyDetails(String name, String description, Boolean active) {
    String trimmedName = name == null ? "" : name.trim();
    if (trimmedName.isEmpty() || trimmedName.length() > NAME_MAX_LENGTH)
      throw new IllegalArgumentException("Invalid role name");
    String trimmedDescription = description == null ? "" : description.trim();
    if (trimmedDescription.length() > DESCRIPTION_MAX_LENGTH)
      throw new IllegalArgumentException("Invalid role description");
    if (active == null) throw new IllegalArgumentException("Active flag is required");
    this.name = trimmedName;
    this.description = trimmedDescription.isEmpty() ? null : trimmedDescription;
    this.active = active;
  }

  public UUID id() {
    return id;
  }

  public String code() {
    return code;
  }

  public String name() {
    return name;
  }

  /** Optional description, or null. */
  public String description() {
    return description;
  }

  public Boolean active() {
    return active;
  }

  public Long rowVersion() {
    return rowVersion;
  }
}
