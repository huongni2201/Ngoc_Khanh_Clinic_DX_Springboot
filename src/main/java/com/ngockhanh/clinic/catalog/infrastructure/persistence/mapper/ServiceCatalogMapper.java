package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery.Service;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery.ServiceItem;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceCatalogMapper {
  List<Service> findByIds(@Param("ids") Set<UUID> ids);

  List<ServiceItem> findActivePage(
      @Param("pattern") String pattern,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long countActive(@Param("pattern") String pattern);
}
