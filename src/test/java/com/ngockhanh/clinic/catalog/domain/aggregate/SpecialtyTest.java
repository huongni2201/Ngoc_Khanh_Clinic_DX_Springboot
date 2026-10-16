package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialtyTest {

  @Test
  void createsActiveSpecialtyWithVersionZero() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("NOI-KHOA");
    Specialty spec = Specialty.create(id, code, "Nội Khoa");

    assertThat(spec.id()).isEqualTo(id);
    assertThat(spec.code()).isEqualTo(code);
    assertThat(spec.name()).isEqualTo("Nội Khoa");
    assertThat(spec.active()).isTrue();
    assertThat(spec.rowVersion()).isZero();
    assertThat(spec.createdAt()).isNull();
    assertThat(spec.updatedAt()).isNull();
  }

  @Test
  void updatesName() {
    Specialty spec = Specialty.create(UUID.randomUUID(), new CatalogCode("NOI"), "Nội");
    spec.updateDetails("Nội Tổng Quát");

    assertThat(spec.name()).isEqualTo("Nội Tổng Quát");
  }

  @Test
  void deactivatesAndReactivates() {
    Specialty spec = Specialty.create(UUID.randomUUID(), new CatalogCode("NOI"), "Nội");
    spec.deactivate();
    assertThat(spec.active()).isFalse();

    spec.reactivate();
    assertThat(spec.active()).isTrue();
  }

  @Test
  void validatesInvariantsOnCreate() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("NOI");

    assertThatThrownBy(() -> Specialty.create(null, code, "Nội"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Specialty.create(id, null, "Nội"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Specialty.create(id, code, "   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Specialty.create(id, code, "A".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoresFromPersistence() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("NGOAI");
    Instant now = Instant.now();
    Specialty spec = Specialty.restore(id, code, "Ngoại", false, now, now, 3L);

    assertThat(spec.id()).isEqualTo(id);
    assertThat(spec.active()).isFalse();
    assertThat(spec.rowVersion()).isEqualTo(3L);
  }
}
