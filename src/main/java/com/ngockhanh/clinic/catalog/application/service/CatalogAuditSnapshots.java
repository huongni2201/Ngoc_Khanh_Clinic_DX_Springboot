package com.ngockhanh.clinic.catalog.application.service;

import com.ngockhanh.clinic.catalog.domain.aggregate.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Safe master-data snapshots, including nullable changes, recorded in the business transaction. */
public final class CatalogAuditSnapshots {
  private CatalogAuditSnapshots() {}

  public static Map<String, Object> of(Department d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("code", d.code().value());
    values.put("name", d.name());
    values.put("departmentType", d.departmentType().name());
    values.put("active", d.active());
    values.put("rowVersion", d.rowVersion());
    return java.util.Collections.unmodifiableMap(values);
  }

  public static Map<String, Object> of(Room d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("code", d.code().value());
    values.put("name", d.name());
    values.put("departmentId", d.departmentId());
    values.put("roomType", d.roomType());
    values.put("active", d.active());
    values.put("rowVersion", d.rowVersion());
    return java.util.Collections.unmodifiableMap(values);
  }

  public static Map<String, Object> of(Specialty d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("code", d.code().value());
    values.put("name", d.name());
    values.put("active", d.active());
    values.put("rowVersion", d.rowVersion());
    return java.util.Collections.unmodifiableMap(values);
  }

  public static Map<String, Object> of(CatalogService d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("code", d.code().value());
    values.put("name", d.name());
    values.put("serviceType", d.serviceType().name());
    values.put("performingDepartmentId", d.performingDepartmentId());
    values.put("specialtyId", d.specialtyId());
    values.put("unitPrice", d.unitPrice());
    values.put("preparationInstruction", d.preparationInstruction());
    values.put("active", d.active());
    values.put("rowVersion", d.rowVersion());
    return java.util.Collections.unmodifiableMap(values);
  }

  public static Map<String, Object> of(Medicine d) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("code", d.code().value());
    values.put("genericName", d.genericName());
    values.put("brandName", d.brandName());
    values.put("strength", d.strength());
    values.put("dosageForm", d.dosageForm());
    values.put("unit", d.unit());
    values.put("active", d.active());
    values.put("rowVersion", d.rowVersion());
    return java.util.Collections.unmodifiableMap(values);
  }
}
