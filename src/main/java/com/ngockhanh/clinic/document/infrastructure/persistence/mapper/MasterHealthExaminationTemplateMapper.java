package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MasterHealthExaminationTemplateMapper {
  @Select(
      "SELECT v.id FROM public.document_template_versions v JOIN public.document_templates t ON"
          + " t.id=v.document_template_id WHERE t.is_active=true AND"
          + " t.is_master_health_examination_form=true AND v.effective_from<=CURRENT_TIMESTAMP AND"
          + " (v.retired_at IS NULL OR v.retired_at>CURRENT_TIMESTAMP)")
  List<UUID> findEffectiveVersions();
}
