package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DocumentTemplateMapperTest {
  private static final String NAMESPACE =
      "com.ngockhanh.clinic.document.infrastructure.persistence.mapper.DocumentTemplateMapper.";

  @Test
  void theMapperXmlParsesAndDeclaresEveryStatement() throws Exception {
    var config = mapperConfiguration();

    assertThat(config.getMappedStatementNames())
        .contains(
            NAMESPACE + "findEffective",
            NAMESPACE + "lockForSeeding",
            NAMESPACE + "hasVersions",
            NAMESPACE + "findDefaultTemplateId",
            NAMESPACE + "insertDefaultTemplate",
            NAMESPACE + "insertFile",
            NAMESPACE + "insertPublishedVersion");
  }

  @Test
  void findsOnlyThePublishedVersionOfTheActiveDefaultTemplateInEffectAtAnInstant() throws Exception {
    String sql = sql("findEffective");

    assertThat(sql)
        .contains(
            "t.template_type=?",
            "t.is_default=true",
            "t.active=true",
            "v.pages_per_record",
            "v.status='PUBLISHED'",
            "v.active_from<=?",
            "v.retired_at IS NULL",
            "v.retired_at>?");
  }

  @Test
  void seedsAsAPublishedSystemVersionWithoutAuthor() throws Exception {
    String sql = sql("insertPublishedVersion");

    assertThat(sql).contains("'PUBLISHED'").doesNotContain("created_by").doesNotContain("published_by");
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
    String resource = "mapper/document/DocumentTemplateMapper.xml";
    try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).as("MyBatis mapper resource %s", resource).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    return config;
  }
}
