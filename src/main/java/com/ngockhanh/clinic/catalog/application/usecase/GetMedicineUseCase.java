package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.port.out.MedicineReadQuery;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gets catalog data for staff with the corresponding action permission. */
@Service
@RequiredArgsConstructor
public class GetMedicineUseCase {
  private final MedicineReadQuery medicineReadQuery;
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
  public MedicineResponse execute(UUID id, UserPrincipal principal) {
    accessPolicy.requireMedicineDetailView(principal);
    if (id == null) throw new IllegalArgumentException("Medicine ID is required");

    return medicineReadQuery
        .findDetailById(id)
        .map(MedicineResponse::fromRead)
        .orElseThrow(() -> new ResourceNotFoundException("Medicine not found"));
  }
}
