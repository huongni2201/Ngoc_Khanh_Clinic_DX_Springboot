package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchParticipantMapperTest {
  @Test
  void updateBindsTheParticipantScopeAndVersionAndGuardsTheLiveBatch() throws Exception {
    var config = new Configuration();
    config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
    String resource = "mapper/healthexamination/HealthExaminationBatchParticipantMyBatisMapper.xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    var participant =
        HealthExaminationBatchParticipantRecord.builder()
            .id(UUID.randomUUID())
            .batchId(UUID.randomUUID())
            .rowVersion(3L)
            .build();
    var bound =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper.update")
            .getBoundSql(Map.of("p", participant, "expectedVersion", 3L));
    assertThat(bound.getSql().replaceAll("\\s+", " "))
        .contains(
            "WHERE id=? AND batch_id=? AND row_version=?",
            "row_version=row_version+1",
            "public.health_examination_batches b",
            "b.deleted_at IS NULL FOR SHARE OF b")
        .doesNotContain(participant.id().toString(), participant.batchId().toString());
    assertThat(bound.getParameterMappings())
        .extracting(mapping -> mapping.getProperty())
        .endsWith("p.id", "p.batchId", "expectedVersion", "p.batchId");
  }
}
