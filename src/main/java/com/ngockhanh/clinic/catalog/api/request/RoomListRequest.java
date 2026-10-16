package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.query.RoomListQuery;
import java.util.UUID;

public record RoomListRequest(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    UUID departmentId,
    String roomType,
    String sortKey,
    String sortBy) {

  public RoomListQuery toQuery() {
    return new RoomListQuery(
        page, size, searchKey, active, departmentId, roomType, sortKey, sortBy);
  }
}
