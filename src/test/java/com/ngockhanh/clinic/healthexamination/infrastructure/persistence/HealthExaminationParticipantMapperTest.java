package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;
import com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationParticipantMapperTest {
  private static final String MAPPER =
      "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper.";

  private static Configuration loadMapper() throws Exception {
    var config = new Configuration();
    config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
    String resource = "mapper/healthexamination/HealthExaminationParticipantMyBatisMapper.xml";
    try (var input =
        HealthExaminationParticipantMapperTest.class
            .getClassLoader()
            .getResourceAsStream(resource)) {
      assertThat(input).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    return config;
  }

  private static String printableSql(Configuration config, boolean includeAttended, String order) {
    return config
        .getMappedStatement(MAPPER + "findPrintableIdsOfSession")
        .getBoundSql(
            Map.of(
                "agreementId",
                UUID.randomUUID(),
                "sessionId",
                UUID.randomUUID(),
                "includeAttended",
                includeAttended,
                "order",
                order))
        .getSql()
        .replaceAll("\\s+", " ")
        .replaceAll("\\s+([,)])", "$1");
  }

  @Test
  void printableSessionNeverIncludesNoShowMovedOrCancelledAndAttendedOnlyOnRequest()
      throws Exception {
    var config = loadMapper();

    assertThat(printableSql(config, false, "DEPARTMENT"))
        .contains("p.roster_status='ACTIVE'", "sa.session_id=?", "sa.status IN ('ASSIGNED')")
        .doesNotContain("ATTENDED", "NO_SHOW", "MOVED", "CANCELLED");
    assertThat(printableSql(config, true, "DEPARTMENT"))
        .contains("sa.status IN ('ASSIGNED','ATTENDED')")
        .doesNotContain("NO_SHOW", "MOVED", "CANCELLED");
  }

  @Test
  void printableSessionOrdersByDepartmentThenImportRowThenCodeUnlessAskedOtherwise()
      throws Exception {
    var config = loadMapper();

    assertThat(printableSql(config, false, "DEPARTMENT"))
        .contains(
            "ORDER BY p.department_name ASC, p.source_row_number ASC NULLS LAST, p.participant_code ASC NULLS LAST, p.id ASC");
    assertThat(printableSql(config, false, "CODE"))
        .contains("ORDER BY p.participant_code ASC NULLS LAST, p.id ASC");
    assertThat(printableSql(config, false, "IMPORT_ORDER"))
        .contains(
            "ORDER BY p.source_row_number ASC NULLS LAST, p.participant_code ASC NULLS LAST, p.id ASC");
  }

  @Test
  void updateBindsTheParticipantScopeAndVersionAndGuardsTheLiveAgreement() throws Exception {
    var config = new Configuration();
    config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
    String resource = "mapper/healthexamination/HealthExaminationParticipantMyBatisMapper.xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    var participant =
        HealthExaminationParticipantRecord.builder()
            .id(UUID.randomUUID())
            .agreementId(UUID.randomUUID())
            .rowVersion(3L)
            .build();
    var bound =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper.update")
            .getBoundSql(Map.of("p", participant, "expectedVersion", 3L));
    assertThat(bound.getSql().replaceAll("\\s+", " "))
        .contains(
            "WHERE id=? AND agreement_id=? AND row_version=?",
            "row_version=row_version+1",
            "public.health_examination_agreements b",
            "b.deleted_at IS NULL FOR SHARE OF b")
        .doesNotContain(participant.id().toString(), participant.agreementId().toString());
    assertThat(bound.getParameterMappings())
        .extracting(mapping -> mapping.getProperty())
        .endsWith("p.id", "p.agreementId", "expectedVersion", "p.agreementId");
  }
}
