package com.ngockhanh.clinic.integration.infrastructure.persistence.mapper;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgreementHistoryMapper {
  boolean existsImportJobForAgreement(@Param("agreementId") UUID agreementId);
}
