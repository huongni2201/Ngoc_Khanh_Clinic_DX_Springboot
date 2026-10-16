package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.UUID;
import lombok.Builder;

/**
 * Role an account holds after a change (SRS UC-010).
 *
 * @param rowVersion incremented account version to send with the next change
 */
@Builder
public record AccountRoleResponse(UUID accountId, String roleCode, Long rowVersion) {}
