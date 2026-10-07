package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MasterHealthExaminationTemplateMapperTest {
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
