package com.ngockhanh.clinic.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuditEventMapperTest {
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
