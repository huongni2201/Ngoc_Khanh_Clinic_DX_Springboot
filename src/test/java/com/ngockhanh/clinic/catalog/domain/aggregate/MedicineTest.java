package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicineTest {

  @Test
  void createsActiveMedicineWithVersionZero() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("PARA-500");
    Medicine med = Medicine.create(id, code, "Paracetamol", "Panadol", "500mg", "Viên nén", "Viên");

    assertThat(med.id()).isEqualTo(id);
    assertThat(med.code()).isEqualTo(code);
    assertThat(med.genericName()).isEqualTo("Paracetamol");
    assertThat(med.brandName()).isEqualTo("Panadol");
    assertThat(med.strength()).isEqualTo("500mg");
    assertThat(med.dosageForm()).isEqualTo("Viên nén");
    assertThat(med.unit()).isEqualTo("Viên");
    assertThat(med.active()).isTrue();
    assertThat(med.rowVersion()).isZero();
    assertThat(med.createdAt()).isNull();
    assertThat(med.updatedAt()).isNull();
  }

  @Test
  void updatesDetails() {
    Medicine med =
        Medicine.create(
            UUID.randomUUID(),
            new CatalogCode("PARA"),
            "Paracetamol",
            null,
            "500mg",
            "Viên nén",
            "Viên");

    med.updateDetails("Paracetamol", "Efferalgan", "500mg", "Viên sủi", "Viên");

    assertThat(med.brandName()).isEqualTo("Efferalgan");
    assertThat(med.dosageForm()).isEqualTo("Viên sủi");
  }

  @Test
  void validatesInvariants() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("MED");

    assertThatThrownBy(() -> Medicine.create(null, code, "G", null, "S", "D", "U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Medicine.create(id, null, "G", null, "S", "D", "U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Medicine.create(id, code, "   ", null, "S", "D", "U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Medicine.create(id, code, "G", null, "  ", "D", "U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Medicine.create(id, code, "G", null, "S", "  ", "U"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Medicine.create(id, code, "G", null, "S", "D", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoresFromPersistence() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("MED-RESTORE");
    Instant now = Instant.now();
    Medicine med =
        Medicine.restore(
            id, code, "Amoxicillin", "Clamoxyl", "500mg", "Viên nang", "Viên", false, now, now, 1L);

    assertThat(med.active()).isFalse();
    assertThat(med.rowVersion()).isEqualTo(1L);
  }
}
