package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.api.response.EmployeeListResponse;
import com.ngockhanh.clinic.healthcheck.application.port.BatchEmployeeSummaryQuery;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.application.query.EmployeeListQuery;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ListBatchEmployeeUseCase {
    private static final Set<String> ALLOWED_SORT_TYPES = Set.of(
            "id", "employeeId", "employeeCode", "fullName", "departmentName", "jobTitle", "occupation",
            "status", "createdAt");

    private final ParticipantImportBatchQuery batchQuery;
    private final BatchEmployeeSummaryQuery employeeQuery;

    public ListBatchEmployeeUseCase(ParticipantImportBatchQuery batchQuery,
                                    BatchEmployeeSummaryQuery employeeQuery) {
        this.batchQuery = batchQuery;
        this.employeeQuery = employeeQuery;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeListResponse> execute(UUID organizationId, UUID batchId, EmployeeListQuery query) {
        if (query == null) throw new IllegalArgumentException("Employee list query is required");

        batchQuery.findById(batchId).filter(batch -> batch.organizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));

        int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
        int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
        if (page < 1 || size < 1 || size > PaginationConstants.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid pagination");
        }

        String sortType = normalize(query.sortType());
        if (sortType == null) sortType = PaginationConstants.DEFAULT_SORTED_TYPE;
        if (!ALLOWED_SORT_TYPES.contains(sortType)) throw new IllegalArgumentException("Invalid sort type");

        String sortBy = query.sortBy() == null || query.sortBy().isBlank()
                ? PaginationConstants.DEFAULT_SORTED_ORDER
                : query.sortBy().trim().toUpperCase(Locale.ROOT);
        if (!sortBy.equals("ASC") && !sortBy.equals("DESC")) {
            throw new IllegalArgumentException("Invalid sort direction");
        }

        String searchKey = normalize(query.searchKey());
        String searchPattern = searchKey == null ? null : toContainsPattern(searchKey);

        long requestedOffset = (page - 1L) * size;
        if (requestedOffset > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pagination");
        int offset = (int) requestedOffset;

        long total = employeeQuery.countByBatch(batchId, searchPattern);
        int pages = (int) ((total + size - 1) / size);
        log.info("Employee roster query completed: batchId={}, page={}, size={}, sortType={}, sortBy={}, total={}",
                batchId, page, size, sortType, sortBy, total);

        return new PageResponse<>(employeeQuery.findByBatch(batchId, offset, size,
                searchPattern, sortType, sortBy), page, size, total, pages);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeListResponse> execute(UUID organizationId, UUID batchId, int page, int size) {
        return execute(organizationId, batchId, EmployeeListQuery.builder().page(page).size(size).build());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String toContainsPattern(String value) {
        String escapedValue = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapedValue + "%";
    }
}
