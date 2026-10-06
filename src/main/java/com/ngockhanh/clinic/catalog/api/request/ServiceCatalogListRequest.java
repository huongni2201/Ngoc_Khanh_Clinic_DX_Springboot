package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "code", "name", "unitPrice"})
public class ServiceCatalogListRequest extends BasePagination {}
