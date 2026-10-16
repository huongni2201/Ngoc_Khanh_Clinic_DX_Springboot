package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.catalog.application.port.out.RoomReadQuery;
import com.ngockhanh.clinic.catalog.application.query.RoomListQuery;
import com.ngockhanh.clinic.catalog.application.response.RoomItemResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.application.service.CatalogQuerySupport;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Lists catalog data for staff with the corresponding action permission. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListRoomsUseCase {
  private static final Set<String> SORT_KEYS = Set.of("id", "code", "name");

  private final RoomReadQuery roomReadQuery;
  private final CatalogAccessPolicy accessPolicy;

  /**
   * Reads catalog data after checking the action permission.
   *
   * @param query validated list filters and pagination
   * @param principal authenticated staff principal with the action permission
   * @return authorized catalog response
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException if access is denied
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<RoomItemResponse> execute(RoomListQuery query, UserPrincipal principal) {
    accessPolicy.requireRoomView(principal);
    if (query == null) throw new IllegalArgumentException("List query is required");

    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");

    String sortKey =
        query.sortKey() == null || query.sortKey().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_KEY
            : query.sortKey();
    if (!SORT_KEYS.contains(sortKey)) throw new IllegalArgumentException("Sort key is not allowed");

    String sortBy =
        query.sortBy() == null || query.sortBy().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_BY
            : query.sortBy().trim().toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC"))
      throw new IllegalArgumentException("Sort direction must be ASC or DESC");

    String pattern = CatalogQuerySupport.likePattern(query.searchKey());
    long offset = (long) (page - 1) * size;
    long total =
        roomReadQuery.count(pattern, query.active(), query.departmentId(), query.roomType());
    List<RoomReadQuery.Item> items =
        total == 0 || offset >= total
            ? List.of()
            : roomReadQuery.findPage(
                pattern,
                query.active(),
                query.departmentId(),
                query.roomType(),
                offset,
                size,
                sortKey,
                sortBy);

    log.debug(
        "Rooms listed: page={}, size={}, returned={}, total={}", page, size, items.size(), total);
    return PageResponse.<RoomItemResponse>builder()
        .items(items.stream().map(RoomItemResponse::fromRead).toList())
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }
}
