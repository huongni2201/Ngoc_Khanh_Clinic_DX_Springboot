package com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServiceRecord;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ServiceCatalogMapper {
  @Select({
    "<script>SELECT"
        + " id,service_code,service_name,service_type,performing_department_id,default_room_id,requires_payment,requires_specimen,health_examination_eligible,result_type,lab_panel_id,preparation_instructions,is_active,created_at,updated_at"
        + " FROM public.services WHERE id IN",
    "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"
  })
  List<ServiceRecord> findByIds(@Param("ids") Set<UUID> ids);
}
