package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DepartmentTest {

  @Test
  void createsActiveDepartmentWithVersionZero() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("KHAM-BENH");
    Department dept = Department.create(id, code, "Khoa Khám Bệnh", DepartmentType.CLINICAL);

    assertThat(dept.id()).isEqualTo(id);
    assertThat(dept.code()).isEqualTo(code);
    assertThat(dept.name()).isEqualTo("Khoa Khám Bệnh");
    assertThat(dept.departmentType()).isEqualTo(DepartmentType.CLINICAL);
    assertThat(dept.active()).isTrue();
    assertThat(dept.rowVersion()).isZero();
    assertThat(dept.createdAt()).isNull();
    assertThat(dept.updatedAt()).isNull();
  }

  @Test
  void updatesNameAndType() {
    Department dept =
        Department.create(
            UUID.randomUUID(), new CatalogCode("KHAM"), "Khoa Khám", DepartmentType.CLINICAL);
    dept.updateDetails("Khoa Cấp Cứu", DepartmentType.CLINICAL);

    assertThat(dept.name()).isEqualTo("Khoa Cấp Cứu");
    assertThat(dept.departmentType()).isEqualTo(DepartmentType.CLINICAL);
  }

  @Test
  void deactivatesAndReactivates() {
    Department dept =
        Department.create(
            UUID.randomUUID(), new CatalogCode("KHAM"), "Khoa Khám", DepartmentType.CLINICAL);
    dept.deactivate();
    assertThat(dept.active()).isFalse();

    dept.reactivate();
    assertThat(dept.active()).isTrue();
  }

  @Test
  void validatesInvariantsOnCreate() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("KHAM");

    assertThatThrownBy(() -> Department.create(null, code, "Khoa", DepartmentType.CLINICAL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Department.create(id, null, "Khoa", DepartmentType.CLINICAL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Department.create(id, code, "   ", DepartmentType.CLINICAL))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Department.create(id, code, "Khoa", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Department.create(id, code, "A".repeat(201), DepartmentType.CLINICAL))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoresFromPersistence() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("CDHA");
    Instant now = Instant.now();
    Department dept =
        Department.restore(
            id, code, "Khoa Chẩn Đoán Hình Ảnh", DepartmentType.DIAGNOSTIC, false, now, now, 5L);

    assertThat(dept.id()).isEqualTo(id);
    assertThat(dept.code()).isEqualTo(code);
    assertThat(dept.active()).isFalse();
    assertThat(dept.rowVersion()).isEqualTo(5L);
  }
}
