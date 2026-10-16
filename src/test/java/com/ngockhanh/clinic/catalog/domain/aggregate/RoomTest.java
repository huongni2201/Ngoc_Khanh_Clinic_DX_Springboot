package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomTest {

  @Test
  void createsActiveRoomWithVersionZero() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("P101");
    UUID deptId = UUID.randomUUID();
    Room room = Room.create(id, code, "Phòng Khám Nội 1", deptId, "EXAMINATION");

    assertThat(room.id()).isEqualTo(id);
    assertThat(room.code()).isEqualTo(code);
    assertThat(room.name()).isEqualTo("Phòng Khám Nội 1");
    assertThat(room.departmentId()).isEqualTo(deptId);
    assertThat(room.roomType()).isEqualTo("EXAMINATION");
    assertThat(room.active()).isTrue();
    assertThat(room.rowVersion()).isZero();
    assertThat(room.createdAt()).isNull();
    assertThat(room.updatedAt()).isNull();
  }

  @Test
  void updatesNameAndRoomType() {
    Room room =
        Room.create(
            UUID.randomUUID(),
            new CatalogCode("P101"),
            "Phòng 101",
            UUID.randomUUID(),
            "EXAMINATION");
    room.updateDetails("Phòng Siêu Âm", "PARACLINICAL");

    assertThat(room.name()).isEqualTo("Phòng Siêu Âm");
    assertThat(room.roomType()).isEqualTo("PARACLINICAL");
  }

  @Test
  void deactivatesAndReactivates() {
    Room room =
        Room.create(
            UUID.randomUUID(),
            new CatalogCode("P101"),
            "Phòng 101",
            UUID.randomUUID(),
            "EXAMINATION");
    room.deactivate();
    assertThat(room.active()).isFalse();

    room.reactivate();
    assertThat(room.active()).isTrue();
  }

  @Test
  void validatesInvariantsOnCreate() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("P101");
    UUID deptId = UUID.randomUUID();

    assertThatThrownBy(() -> Room.create(null, code, "Phòng", deptId, "EXAMINATION"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Room.create(id, null, "Phòng", deptId, "EXAMINATION"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Room.create(id, code, "   ", deptId, "EXAMINATION"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Room.create(id, code, "Phòng", null, "EXAMINATION"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Room.create(id, code, "Phòng", deptId, "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoresFromPersistence() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("P201");
    UUID deptId = UUID.randomUUID();
    Instant now = Instant.now();
    Room room = Room.restore(id, code, "Phòng 201", deptId, "SUPPORT", false, now, now, 2L);

    assertThat(room.id()).isEqualTo(id);
    assertThat(room.active()).isFalse();
    assertThat(room.rowVersion()).isEqualTo(2L);
  }
}
