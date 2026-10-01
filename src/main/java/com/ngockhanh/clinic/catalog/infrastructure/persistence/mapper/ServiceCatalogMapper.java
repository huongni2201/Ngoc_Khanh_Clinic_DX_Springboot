package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery.Service;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceCatalogMapper {
  List<Service> findByIds(@Param("ids") Set<UUID> ids);
}
