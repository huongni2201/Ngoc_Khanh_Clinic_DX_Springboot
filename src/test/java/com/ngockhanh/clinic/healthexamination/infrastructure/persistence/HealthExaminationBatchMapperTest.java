package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class HealthExaminationBatchMapperTest {
  @Test
  void parsesXmlAndSafelyBuildsScopedParameterizedQueries() throws Exception {
    var config = new Configuration();
    config
        .getTypeHandlerRegistry()
        .register(
            UUID.class, com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler.class);
    String resource = "mapper/health-examination/HealthExaminationBatchMyBatisMapper.xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
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
}
