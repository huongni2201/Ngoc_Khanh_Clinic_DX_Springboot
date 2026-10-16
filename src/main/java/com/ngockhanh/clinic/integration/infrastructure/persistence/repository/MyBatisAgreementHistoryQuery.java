package com.ngockhanh.clinic.integration.infrastructure.persistence.repository;

import com.ngockhanh.clinic.integration.application.query.AgreementHistoryQuery;
import com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.AgreementHistoryMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisAgreementHistoryQuery implements AgreementHistoryQuery {
  private final AgreementHistoryMapper mapper;

  @Override
  public boolean hasAgreementReferences(UUID agreementId) {
    if (agreementId == null) throw new IllegalArgumentException("Agreement ID is required");
    return mapper.existsImportJobForAgreement(agreementId);
  }
}
