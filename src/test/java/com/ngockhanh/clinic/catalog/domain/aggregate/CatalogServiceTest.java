package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

  @Test
  void createsActiveServiceWithVersionZero() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("XRAY-CHEST");
    UUID deptId = UUID.randomUUID();
    UUID specId = UUID.randomUUID();
    BigDecimal price = new BigDecimal("250000.00");

    CatalogService service =
        CatalogService.create(
            id,
            code,
            "Chụp X-Quang Ngực Thẳng",
            ServiceType.XRAY,
            deptId,
            specId,
            price,
            "Tháo đồ trang sức kim loại vùng ngực");

    assertThat(service.id()).isEqualTo(id);
    assertThat(service.code()).isEqualTo(code);
    assertThat(service.name()).isEqualTo("Chụp X-Quang Ngực Thẳng");
    assertThat(service.serviceType()).isEqualTo(ServiceType.XRAY);
    assertThat(service.performingDepartmentId()).isEqualTo(deptId);
    assertThat(service.specialtyId()).isEqualTo(specId);
    assertThat(service.unitPrice()).isEqualByComparingTo(price);
    assertThat(service.preparationInstruction()).isEqualTo("Tháo đồ trang sức kim loại vùng ngực");
    assertThat(service.active()).isTrue();
    assertThat(service.rowVersion()).isZero();
    assertThat(service.createdAt()).isNull();
    assertThat(service.updatedAt()).isNull();
  }

  @Test
  void updatesDetails() {
    CatalogService service =
        CatalogService.create(
            UUID.randomUUID(),
            new CatalogCode("XRAY"),
            "Chụp X-Quang",
            ServiceType.XRAY,
            UUID.randomUUID(),
            null,
            new BigDecimal("100000.00"),
            null);

    UUID newSpecId = UUID.randomUUID();
    service.updateDetails(
        "Chụp X-Quang Kỹ Thuật Số",
        ServiceType.XRAY,
        newSpecId,
        new BigDecimal("120000.00"),
        "Không có");

    assertThat(service.name()).isEqualTo("Chụp X-Quang Kỹ Thuật Số");
    assertThat(service.specialtyId()).isEqualTo(newSpecId);
    assertThat(service.unitPrice()).isEqualByComparingTo("120000.00");
    assertThat(service.preparationInstruction()).isEqualTo("Không có");
  }

  @Test
  void validatesInvariants() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("SERV");
    UUID deptId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                CatalogService.create(
                    null, code, "Name", ServiceType.LAB, deptId, null, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(
                    id, null, "Name", ServiceType.LAB, deptId, null, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(
                    id, code, "   ", ServiceType.LAB, deptId, null, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(id, code, "Name", null, deptId, null, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(
                    id, code, "Name", ServiceType.LAB, null, null, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(id, code, "Name", ServiceType.LAB, deptId, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(
                    id, code, "Name", ServiceType.LAB, deptId, null, new BigDecimal("-1.00"), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CatalogService.create(
                    id,
                    code,
                    "Name",
                    ServiceType.LAB,
                    deptId,
                    null,
                    new BigDecimal("100.999"),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoresFromPersistence() {
    UUID id = UUID.randomUUID();
    CatalogCode code = new CatalogCode("LAB-BLOOD");
    Instant now = Instant.now();
    CatalogService s =
        CatalogService.restore(
            id,
            code,
            "Xét nghiệm máu",
            ServiceType.LAB,
            UUID.randomUUID(),
            null,
            new BigDecimal("80000.00"),
            null,
            false,
            now,
            now,
            4L);

    assertThat(s.active()).isFalse();
    assertThat(s.rowVersion()).isEqualTo(4L);
  }
}
