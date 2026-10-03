package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchMapperTest {
  @Test
  void parsesXmlAndSafelyBuildsScopedParameterizedQueries() throws Exception {
    var config = mapperConfiguration("health-examination", "HealthExaminationBatchMyBatisMapper");
    String namespace =
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper.";
    var params = new HashMap<String, Object>();
    params.put("organizationId", UUID.randomUUID());
    params.put("id", UUID.randomUUID());
    params.put("lock", true);
    String scoped =
        config.getMappedStatement(namespace + "findScoped").getBoundSql(params).getSql();
    assertThat(scoped).contains("organization_id=?", "id=?", "FOR UPDATE");
    params.put("pattern", "evil_%");
    params.put("sortKey", "id; DROP TABLE services");
    params.put("sortBy", "DESC; DROP TABLE services");
    params.put("offset", 0);
    params.put("limit", 10);
    var sql = config.getMappedStatement(namespace + "findPage").getBoundSql(params).getSql();
    assertThat(sql)
        .contains("ESCAPE chr(92)", "status!='DELETED'")
        .doesNotContain("DROP TABLE", "evil_%");
    params.put("retained", List.of(UUID.randomUUID()));
    assertThat(
            config
                .getMappedStatement(namespace + "hasReferencedRemoved")
                .getBoundSql(params)
                .getSql())
        .contains("resolved_batch_service_id", "health_examination_batch_service_id");
  }

  @Test
  void selectsOnlyCurrentMasterTemplatesFromDocumentXml() throws Exception {
    var config = mapperConfiguration("document", "MasterHealthExaminationTemplateMapper");
    String sql =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.document.infrastructure.persistence.mapper.MasterHealthExaminationTemplateMapper.findEffectiveVersions")
            .getBoundSql(Map.of())
            .getSql();
    assertThat(sql)
        .contains(
            "FROM public.document_template_versions v",
            "JOIN public.document_templates t ON",
            "t.is_active=true",
            "t.is_master_health_examination_form=true",
            "v.effective_from<=CURRENT_TIMESTAMP",
            "v.retired_at IS NULL",
            "v.retired_at>CURRENT_TIMESTAMP");
  }

  @Test
  void bindsSharedAuditInsertFieldsFromXml() throws Exception {
    var config = mapperConfiguration("shared", "AuditLogMapper");
    var bound =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.shared.infrastructure.persistence.mapper.AuditLogMapper.insert")
            .getBoundSql(
                Map.of(
                    "id", UUID.randomUUID(),
                    "actorUserId", UUID.randomUUID(),
                    "action", "TEST_ACTION",
                    "entityType", "TEST_ENTITY",
                    "entityId", "test-id",
                    "beforeJson", "{}",
                    "afterJson", "{}"));
    assertThat(bound.getSql())
        .contains(
            "INSERT INTO public.audit_logs", "CURRENT_TIMESTAMP", "before_json", "after_json");
    assertThat(bound.getParameterMappings())
        .extracting(mapping -> mapping.getProperty())
        .containsExactly(
            "id", "actorUserId", "action", "entityType", "entityId", "beforeJson", "afterJson");
  }

  private Configuration mapperConfiguration(String module, String mapper) throws Exception {
    var config = new Configuration();
    config
        .getTypeHandlerRegistry()
        .register(
            UUID.class, com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler.class);
    String resource = "mapper/" + module + "/" + mapper + ".xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).as("MyBatis mapper resource %s", resource).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    return config;
  }
}
