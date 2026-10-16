package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.port.out.ManagedServiceReadQuery;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gets catalog data for staff with the corresponding action permission. */
@Service
@RequiredArgsConstructor
public class GetManagedServiceUseCase {
  private final ManagedServiceReadQuery serviceReadQuery;
  private final CatalogAccessPolicy accessPolicy;

  /**
   * Reads catalog data after checking the action permission.
   *
   * @param id catalog resource identifier
   * @param principal authenticated staff principal with the action permission
   * @return authorized catalog response
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException if access is denied
   */
  @Transactional(readOnly = true)
  public ManagedServiceResponse execute(UUID id, UserPrincipal principal) {
    accessPolicy.requireServiceDetailView(principal);
    if (id == null) throw new IllegalArgumentException("Service ID is required");

    return serviceReadQuery
        .findDetailById(id)
        .map(ManagedServiceResponse::fromRead)
        .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
  }
}
