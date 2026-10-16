package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationAgreementRepository.AgreementSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationAgreementMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisHealthExaminationAgreementRepository;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.HealthExaminationAgreementSummaryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisHealthExaminationAgreementRepositoryTest {
  @Test
  void mapsTheSqlSummaryProjectionToPageSummary() {
    var mapper = mock(HealthExaminationAgreementMyBatisMapper.class);
    var repository = new MyBatisHealthExaminationAgreementRepository(mapper);
    var organizationId = UUID.randomUUID();
    var agreementId = UUID.randomUUID();
    var createdAt = Instant.parse("2026-01-01T10:00:00Z");
    var updatedAt = Instant.parse("2026-01-02T10:00:00Z");
    var firstDate = LocalDate.of(2026, 1, 10);
    var lastDate = LocalDate.of(2026, 1, 12);
    when(mapper.findPage(organizationId, 0, 10, null, "agreementCode", "ASC"))
        .thenReturn(
            List.of(
                new HealthExaminationAgreementSummaryView(
                    agreementId,
                    "AGREEMENT-001",
                    "Annual examination",
                    firstDate,
                    lastDate,
                    "DRAFT",
                    createdAt,
                    updatedAt,
                    3)));

    var result = repository.findPage(organizationId, 0, 10, null, "agreementCode", "ASC");

    assertThat(result)
        .containsExactly(
            new AgreementSummary(
                agreementId,
                "AGREEMENT-001",
                "Annual examination",
                firstDate,
                lastDate,
                AgreementStatus.DRAFT,
                createdAt,
                updatedAt,
                3));
  }
}
