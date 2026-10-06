package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter.OrganizationPersistenceConverter;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.OrganizationMyBatisMapper;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisOrganizationRepository implements OrganizationRepository {
  private final OrganizationMyBatisMapper mapper;
  private final OrganizationPersistenceConverter converter = new OrganizationPersistenceConverter();

  @Override
  public Optional<Organization> findById(AggregateId id) {
    return Optional.ofNullable(converter.toDomain(mapper.findById(id.value())));
  }

  @Override
  public boolean existsByTaxCode(String taxCode, AggregateId excludedOrganizationId) {
    if (taxCode == null || taxCode.isBlank()) return false;
    return mapper.existsByTaxCode(
        taxCode, excludedOrganizationId == null ? null : excludedOrganizationId.value());
  }

  @Override
  public void save(Organization organization) {
    if (mapper.insert(converter.toRecord(organization)) != 1) {
      throw new IllegalStateException("Organization was not inserted");
    }
  }

  @Override
  public void update(Organization organization, long expectedRowVersion) {
    if (mapper.update(converter.toRecord(organization), expectedRowVersion) != 1) {
      throw new ConcurrentUpdateException();
    }
  }

  @Override
  public PageResponse<Organization> search(
      int page, int size, String searchKey, String sortKey, String sortBy, String status) {
    long offset = ((long) page - 1) * size;
    String pattern = likePattern(searchKey);
    long total = mapper.count(status, pattern);
    List<Organization> items =
        total == 0 || offset >= total
            ? List.of()
            : mapper.search(status, pattern, sortKey, sortBy, size, offset).stream()
                .map(converter::toDomain)
                .toList();
    return PageResponse.<Organization>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String searchKey) {
    if (searchKey == null || searchKey.isBlank()) return null;
    String escaped = searchKey.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
