package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.port.out.RoomReadQuery;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gets catalog data for staff with the corresponding action permission. */
@Service
@RequiredArgsConstructor
public class GetRoomUseCase {
  private final RoomReadQuery roomReadQuery;
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
  public RoomResponse execute(UUID id, UserPrincipal principal) {
    accessPolicy.requireRoomDetailView(principal);
    if (id == null) throw new IllegalArgumentException("Room ID is required");

    return roomReadQuery
        .findDetailById(id)
        .map(RoomResponse::fromRead)
        .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
  }
}
