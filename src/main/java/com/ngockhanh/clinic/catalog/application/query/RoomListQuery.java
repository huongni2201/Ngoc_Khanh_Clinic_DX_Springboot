package com.ngockhanh.clinic.catalog.application.query;

import java.util.UUID;

public record RoomListQuery(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    UUID departmentId,
    String roomType,
    String sortKey,
    String sortBy) {}
