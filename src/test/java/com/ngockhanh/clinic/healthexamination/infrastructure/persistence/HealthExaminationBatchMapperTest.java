package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchMapperTest {
  private static final String NAMESPACE =
      "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchMyBatisMapper.";

  private static String sql(Configuration config, String statement, Map<String, Object> params) {
    return config
        .getMappedStatement(NAMESPACE + statement)
        .getBoundSql(params)
        .getSql()
        .replaceAll("\\s+", " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .replace(" , ", ",");
  }

  @Test
  void parsesXmlAndSafelyBuildsScopedParameterizedQueries() throws Exception {
    var config = mapperConfiguration("healthexamination", "HealthExaminationBatchMyBatisMapper");
    var params = new HashMap<String, Object>();
    params.put("organizationId", UUID.randomUUID());
    params.put("id", UUID.randomUUID());
    params.put("lock", true);
    assertThat(sql(config, "findScoped", params))
        .contains("organization_id=?", "id=?", "deleted_at IS NULL", "FOR UPDATE");
    assertThat(sql(config, "findByIdAndOrganizationId", params))
        .contains("organization_id=?", "deleted_at IS NULL");
    assertThat(sql(config, "findByIdAndOrganizationIdForUpdate", params))
        .contains("deleted_at IS NULL", "FOR UPDATE");
    params.put("pattern", "evil_%");
    params.put("sortKey", "id; DROP TABLE services");
    params.put("sortBy", "DESC; DROP TABLE services");
    params.put("offset", 0);
    params.put("limit", 10);
    var sql = sql(config, "findPage", params);
    assertThat(sql)
        .contains(
            "organization_id=?",
            "deleted_at IS NULL",
            "ESCAPE chr(92)",
            "public.health_examination_batch_days",
            "LIMIT ? OFFSET ?",
            "id ASC")
        .doesNotContain("DROP TABLE", "evil_%");
    assertThat(sql(config, "count", params))
        .contains("organization_id=?", "deleted_at IS NULL");
    params.put("sortKey", "batchCode");
    params.put("sortBy", "ASC");
    assertThat(sql(config, "findPage", params)).contains("batch_code ASC, id ASC");
  }

  @Test
  void everyAllowedSortKeyHasAStableTieBreakerAndTheDefaultIsASingleIdOrder() throws Exception {
    var config = mapperConfiguration("healthexamination", "HealthExaminationBatchMyBatisMapper");
    var params = new HashMap<String, Object>();
    params.put("organizationId", UUID.randomUUID());
    params.put("offset", 0);
    params.put("limit", 10);
    var columns =
        Map.of(
            "batchCode", "batch_code",
            "batchName", "name",
            "startDate", "start_date",
            "status", "status",
            "createdAt", "created_at");
    for (var column : columns.entrySet()) {
      for (String direction : List.of("ASC", "DESC")) {
        params.put("sortKey", column.getKey());
        params.put("sortBy", direction);
        assertThat(sql(config, "findPage", params))
            .as(column.getKey() + " " + direction)
            .contains("ORDER BY")
            .contains(column.getValue() + " " + direction + ", id ASC");
      }
    }
    params.put("sortKey", "id");
    params.put("sortBy", "DESC");
    assertThat(sql(config, "findPage", params)).contains("id DESC").doesNotContain("id ASC");
    params.put("sortBy", "ASC");
    assertThat(sql(config, "findPage", params)).contains("id ASC").doesNotContain("id DESC");
  }

  @Test
  void writesAreGuardedByVersionLiveRowAndDraftStatusAndBindOnlyParameters() throws Exception {
    var config = mapperConfiguration("healthexamination", "HealthExaminationBatchMyBatisMapper");
    var batch = new HashMap<String, Object>();
    batch.put("id", UUID.randomUUID());
    batch.put("organizationId", UUID.randomUUID());
    batch.put("batchCode", "B1");
    batch.put("name", "N");
    batch.put("examinationSiteType", "CLINIC");
    batch.put("examinationSiteName", "S");
    batch.put("examinationSiteAddress", "A");
    var update = new HashMap<String, Object>();
    update.put("batch", batch);
    update.put("expectedRowVersion", 1L);
    assertThat(sql(config, "updateHeader", update))
        .contains(
            "row_version=row_version+1",
            "row_version=?",
            "deleted_at IS NULL",
            "status='DRAFT'",
            "organization_id=?")
        .doesNotContain("B1");

    var delete = new HashMap<String, Object>();
    delete.put("id", UUID.randomUUID());
    delete.put("organizationId", UUID.randomUUID());
    delete.put("expectedRowVersion", 1L);
    delete.put("deletedAt", java.time.Instant.now());
    assertThat(sql(config, "softDelete", delete))
        .contains(
            "SET deleted_at=?",
            "row_version=row_version+1",
            "row_version=?",
            "deleted_at IS NULL",
            "status='DRAFT'")
        .doesNotContain("DELETE FROM");

    var scoped = new HashMap<String, Object>();
    scoped.put("batchId", UUID.randomUUID());
    scoped.put("ids", List.of(UUID.randomUUID(), UUID.randomUUID()));
    scoped.put("dayIds", scoped.get("ids"));
    scoped.put("serviceIds", scoped.get("ids"));
    assertThat(sql(config, "deleteDays", scoped)).contains("batch_id=?", "id IN (?,?)");
    assertThat(sql(config, "deleteServices", scoped)).contains("batch_id=?", "id IN (?,?)");
    assertThat(sql(config, "findReferencedDayIds", scoped))
        .contains("health_examination_batch_participants", "batch_id=?", "batch_day_id IN (?,?)");
    assertThat(sql(config, "findReferencedBatchServiceIds", scoped))
        .contains(
            "health_examination_participant_services", "batch_id=?", "batch_service_id IN (?,?)");
    assertThat(sql(config, "hasParticipants", scoped))
        .contains("SELECT EXISTS", "health_examination_batch_participants", "batch_id=?");

    var service = new HashMap<String, Object>();
    service.put("id", UUID.randomUUID());
    service.put("batchId", UUID.randomUUID());
    service.put("negotiatedPrice", java.math.BigDecimal.ONE);
    service.put("displayOrder", 2);
    service.put("expectedRowVersion", 0L);
    assertThat(sql(config, "updateService", service))
        .contains("negotiated_price=?", "display_order=?", "row_version=row_version+1", "row_version=?");
    assertThat(sql(config, "moveServiceOrder", service)).contains("display_order=?", "batch_id=?");
  }

  @Test
  void participantWritesCannotTouchASoftDeletedBatch() throws Exception {
    var config =
        mapperConfiguration("healthexamination", "HealthExaminationBatchParticipantMyBatisMapper");
    String text;
    try (var input =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "mapper/healthexamination/HealthExaminationBatchParticipantMyBatisMapper.xml")) {
      text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    assertThat(config.getMappedStatementNames()).isNotEmpty();
    assertThat(text)
        .contains("public.health_examination_batches b", "b.deleted_at IS NULL", "FOR SHARE OF b");
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
