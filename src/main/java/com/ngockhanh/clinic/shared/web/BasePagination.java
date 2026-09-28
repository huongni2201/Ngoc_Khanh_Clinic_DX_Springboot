package com.ngockhanh.clinic.shared.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BasePagination {

  @Min(value = PaginationConstants.DEFAULT_PAGE_NUMBER, message = "Page must be greater than or equal to 1")
  @Builder.Default
  private Integer page =  PaginationConstants.DEFAULT_PAGE_NUMBER;

  @Min(value = 1, message = "Size must be greater than or equal to 1")
  @Builder.Default
  private Integer size = PaginationConstants.DEFAULT_PAGE_SIZE;

  @Size(max = 100, message = "SearchKey must not exceed 100 characters")
  private String searchKey;

  @Pattern(regexp = "(?i)^(asc|desc)$", message = "SortBy must be ASC or DESC")
  @Builder.Default
  private String sortBy = PaginationConstants.DEFAULT_SORTED_BY;

  @Size(max = 100, message = "SortKey must not exceed 100 characters")
  @Builder.Default
  private String sortKey =  PaginationConstants.DEFAULT_SORTED_KEY;
}
