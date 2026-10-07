package com.ngockhanh.clinic.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.shared.infrastructure.mybatis.UuidTypeHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ServiceCatalogMapperTest {
  private static final String NAMESPACE =
      "com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.ServiceCatalogMapper.";

  private static Configuration mapperConfiguration() throws Exception {
    var config = new Configuration();
    config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
    String resource = "mapper/catalog/ServiceCatalogMapper.xml";
    try (var input = ServiceCatalogMapperTest.class.getClassLoader().getResourceAsStream(resource)) {
      assertThat(input).as("MyBatis mapper resource %s", resource).isNotNull();
      new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
    }
    return config;
  }

  private static String sql(Configuration config, String statement, Map<String, Object> params) {
    return config
        .getMappedStatement(NAMESPACE + statement)
        .getBoundSql(params)
        .getSql()
        .replaceAll("\\s+", " ");
  }

  @Test
  void pageAndCountOnlySeeActiveServicesAndBindTheSearchPattern() throws Exception {
    var config = mapperConfiguration();
    var params = new HashMap<String, Object>();
    params.put("pattern", "evil_%");
    params.put("sortKey", "id; DROP TABLE services");
    params.put("sortBy", "DESC; DROP TABLE services");
    params.put("offset", 0);
    params.put("limit", 10);

    var page = sql(config, "findActivePage", params);
    assertThat(page)
        .contains("active = true", "ESCAPE chr(92)", "LIMIT ? OFFSET ?", "id ASC")
        .doesNotContain("DROP TABLE", "evil_%");
    assertThat(sql(config, "countActive", params)).contains("active = true", "ESCAPE chr(92)");

    params.put("pattern", null);
    assertThat(sql(config, "findActivePage", params)).doesNotContain("LIKE");
  }

  @Test
  void everyAllowedSortKeyHasAStableTieBreakerAndTheDefaultIsASingleIdOrder() throws Exception {
    var config = mapperConfiguration();
    var params = new HashMap<String, Object>();
    params.put("offset", 0);
    params.put("limit", 10);
    var columns = Map.of("code", "code", "name", "name", "unitPrice", "unit_price");
    for (var column : columns.entrySet()) {
      for (String direction : List.of("ASC", "DESC")) {
        params.put("sortKey", column.getKey());
        params.put("sortBy", direction);
        assertThat(sql(config, "findActivePage", params))
            .as(column.getKey() + " " + direction)
            .contains(column.getValue() + " " + direction + ", id ASC");
      }
    }
    params.put("sortKey", "id");
    params.put("sortBy", "DESC");
    assertThat(sql(config, "findActivePage", params)).contains("id DESC").doesNotContain("id ASC");
  }
}
