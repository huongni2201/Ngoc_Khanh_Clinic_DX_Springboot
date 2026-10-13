package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class RenderJobMapperTest {
  private static final String NAMESPACE =
      "com.ngockhanh.clinic.document.infrastructure.persistence.mapper.RenderJobMapper.";

  @Test
  void theMapperXmlParsesAndDeclaresEveryStatementOfTheInterface() throws Exception {
    var config = mapperConfiguration();

    assertThat(config.getMappedStatementNames())
        .contains(
            NAMESPACE + "findById",
            NAMESPACE + "findByIdempotencyKey",
            NAMESPACE + "countActive",
            NAMESPACE + "insert",
            NAMESPACE + "findParts",
            NAMESPACE + "failExhausted",
            NAMESPACE + "claimNext",
            NAMESPACE + "recordTemplateVersion",
            NAMESPACE + "progress",
            NAMESPACE + "lockForPart",
            NAMESPACE + "insertFile",
            NAMESPACE + "insertPart",
            NAMESPACE + "finish",
            NAMESPACE + "findExpired",
            NAMESPACE + "deleteParts",
            NAMESPACE + "deleteFilesOfParts",
            NAMESPACE + "markExpired");
  }

  @Test
  void claimingTakesOneWaitingJobAndSkipsOnesAnotherWorkerHasLocked() throws Exception {
    String sql = sql("claimNext");

    assertThat(sql)
        .contains(
            "FOR UPDATE SKIP LOCKED",
            "LIMIT 1",
            "c.status='QUEUED'",
            "c.status='RUNNING' AND c.locked_until < ?",
            "c.attempt_count < ?",
            "ORDER BY c.created_at, c.id",
            "attempt_count=j.attempt_count+1",
            "RETURNING");
  }

  @Test
  void everyWriteOfARunningJobIsGuardedByItsLeaseToken() throws Exception {
    for (String statement : new String[] {"recordTemplateVersion", "progress", "lockForPart", "finish"})
      assertThat(sql(statement)).as(statement).contains("lock_token=?").contains("status='RUNNING'");
  }

  @Test
  void aRepeatedKeyOfTheSameRequesterInsertsNothing() throws Exception {
    assertThat(sql("insert")).contains("ON CONFLICT (requested_by, idempotency_key) DO NOTHING");
  }

  @Test
  void onlyFinishedJobsPastTheirExpiryAreCleaned() throws Exception {
    assertThat(sql("findExpired")).contains("status IN ('SUCCEEDED','FAILED')", "expires_at <= ?");
    assertThat(sql("markExpired")).contains("status IN ('SUCCEEDED','FAILED')");
  }

  @Test
  void aJobIsReadWithIdentifiersOnlyAndTheListsAsText() throws Exception {
    String sql = sql("findById");

    assertThat(sql).contains("array_to_string(j.source_ids, ',') AS record_ids", "j.skipped::text AS skipped");
    assertThat(sql).doesNotContain("full_name");
  }

  private String sql(String statement) throws Exception {
    return mapperConfiguration()
        .getMappedStatement(NAMESPACE + statement)
        .getBoundSql(Map.of())
        .getSql()
        .replaceAll("\\s+", " ");
  }

  private Configuration mapperConfiguration() throws Exception {
    var config = new Configuration();
    config
        .getTypeHandlerRegistry()
        .register(
            UUID.class, com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler.class);
    String resource = "mapper/document/RenderJobMapper.xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).as("MyBatis mapper resource %s", resource).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    return config;
  }
}
