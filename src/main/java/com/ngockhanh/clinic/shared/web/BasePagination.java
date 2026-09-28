package com.ngockhanh.clinic.shared.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BasePagination {

  @Min(value = PaginationConstants.DEFAULT_PAGE_NUMBER, message = "Page must be greater than or equal to 1")
  private Integer page;

  @Min(value = 1, message = "Size must be greater than or equal to 1")
  @Max(value = PaginationConstants.MAX_PAGE_SIZE, message = "Size must be less than or equal to 100")
  private Integer size;

  @Size(max = 100, message = "SearchKey must not exceed 100 characters")
  private String searchKey;

  @Pattern(regexp = "(?i)^(asc|desc)$", message = "SortBy must be ASC or DESC")
  private String sortBy;

  @Size(max = 100, message = "SortType must not exceed 100 characters")
  private String sortType;
}
