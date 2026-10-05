package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchMapperTest {
  @Test
  void parsesXmlAndSafelyBuildsScopedParameterizedQueries() throws Exception {
    var config = mapperConfiguration("healthexamination", "HealthExaminationBatchMyBatisMapper");
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
        .contains(
            "organization_id=?",
            "ESCAPE chr(92)",
            "public.health_examination_batch_days",
            "LIMIT ? OFFSET ?",
            "id ASC")
        .doesNotContain("DROP TABLE", "evil_%");
    assertThat(config.getMappedStatement(namespace + "count").getBoundSql(params).getSql())
        .contains("organization_id=?");
    params.put("sortKey", "batchCode");
    params.put("sortBy", "ASC");
    assertThat(config.getMappedStatement(namespace + "findPage").getBoundSql(params).getSql())
        .contains("batch_code", "ASC", "id ASC");
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
            "FROM public.template_versions v",
            "JOIN public.templates t ON",
            "t.active=true",
            "v.render_mode='MASTER_FORM'",
            "v.active_from<=CURRENT_TIMESTAMP",
            "v.retired_at IS NULL",
            "v.retired_at>CURRENT_TIMESTAMP");
  }

  @Test
  void bindsAuditEventInsertFieldsFromXml() throws Exception {
    var config = mapperConfiguration("audit", "AuditEventMapper");
    var bound =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditEventMapper.insert")
            .getBoundSql(
                Map.of(
                    "id",
                    UUID.randomUUID(),
                    "actorAccountId",
                    UUID.randomUUID(),
                    "action",
                    "TEST_ACTION",
                    "resourceType",
                    "TEST_ENTITY",
                    "resourceId",
                    UUID.randomUUID(),
                    "metadata",
                    "{}"));
    assertThat(bound.getSql())
        .contains("INSERT INTO public.audit_events", "actor_account_id", "metadata");
    assertThat(bound.getParameterMappings())
        .extracting(mapping -> mapping.getProperty())
        .containsExactly(
            "id",
            "occurredAt",
            "actorAccountId",
            "action",
            "resourceType",
            "resourceId",
            "departmentId",
            "correlationId",
            "metadata");
  }

  @Test
  void countsAndPagesImportRowsInSql() throws Exception {
    var config = mapperConfiguration("integration", "ImportMapper");
    UUID jobId = UUID.randomUUID();
    var countSql =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.ImportMapper.countRows")
            .getBoundSql(Map.of("jobId", jobId))
            .getSql();
    var pageSql =
        config
            .getMappedStatement(
                "com.ngockhanh.clinic.integration.infrastructure.persistence.mapper.ImportMapper.pageRows")
            .getBoundSql(Map.of("jobId", jobId, "offset", 100, "limit", 50))
            .getSql();

    assertThat(countSql).contains("COUNT(*)", "public.import_rows", "job_id=?");
    assertThat(pageSql)
        .contains("public.import_rows", "job_id=?", "ORDER BY row_number", "LIMIT ? OFFSET ?");
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
