package com.ngockhanh.clinic.document.infrastructure.persistence.repository;

import com.ngockhanh.clinic.document.application.MasterHealthExaminationTemplateQuery;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.MasterHealthExaminationTemplateMapper;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisMasterHealthExaminationTemplateQuery
    implements MasterHealthExaminationTemplateQuery {
  private final MasterHealthExaminationTemplateMapper mapper;

  public Optional<UUID> findEffectiveVersion() {
    var ids = mapper.findEffectiveVersions();
    if (ids.size() > 1)
      throw new BusinessRuleException("Multiple effective master health-examination templates");
    return ids.stream().findFirst();
  }
}
