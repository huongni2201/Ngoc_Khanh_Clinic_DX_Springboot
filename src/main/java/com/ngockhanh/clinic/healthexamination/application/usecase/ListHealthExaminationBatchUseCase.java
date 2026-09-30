package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.*;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListHealthExaminationBatchUseCase {
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;

  @Transactional(readOnly = true)
  public PageResponse<BatchSummaryResponse> execute(UUID org, HealthExaminationBatchListQuery q) {
    if (q == null) throw new IllegalArgumentException("Query required");
    int page = q.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : q.page();
    int size = q.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : q.size();
    if (page < 1 || size < 1 || size > PaginationConstants.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("Invalid batch pagination");
    }
    String key = q.sortKey() == null || q.sortKey().isBlank() ? "id" : q.sortKey().trim();
    if (!Set.of("id", "batchCode", "batchName", "startDate", "status", "createdAt").contains(key)) {
      throw new IllegalArgumentException("Invalid sort key");
    }
    String direction = q.sortBy() == null || q.sortBy().isBlank() ? "ASC" : q.sortBy().trim().toUpperCase(Locale.ROOT);
    if (!direction.equals("ASC") && !direction.equals("DESC")) {
      throw new IllegalArgumentException("Invalid sort direction");
    }

    organizations
        .findById(new AggregateId(org))
        .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    String term = q.searchKey() == null ? null : q.searchKey().trim().toLowerCase(Locale.ROOT);
    String slash = Character.toString(92);
    String pattern =
        term == null || term.isEmpty()
            ? null
            : "%"
                + term.replace(slash, slash + slash)
                    .replace("%", slash + "%")
                    .replace("_", slash + "_")
                + "%";
    long total = batches.count(org, pattern);
    long pages = total == 0 ? 0 : (total - 1) / size + 1;
    log.debug("List batches: organizationId={}, page={}, size={}", org, page, size);
    return new PageResponse<>(
        batches.findPage(org, (page - 1L) * size, size, pattern, key, direction).stream()
            .map(BatchSummaryResponse::from)
            .toList(),
        page,
        size,
        total,
        (int) Math.min(Integer.MAX_VALUE, pages));
  }
}
