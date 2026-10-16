package com.ngockhanh.clinic.catalog.application.usecase;

import static com.ngockhanh.clinic.catalog.CatalogTestFixtures.manager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.catalog.application.command.CreateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.domain.aggregate.Medicine;
import com.ngockhanh.clinic.catalog.domain.repository.MedicineRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MedicineUseCasesTest {
  private final MedicineRepository medicineRepository = mock(MedicineRepository.class);
  private final CatalogAccessPolicy accessPolicy = new CatalogAccessPolicy();
  private final AuditWriter audit = mock(AuditWriter.class);

  private CreateMedicineUseCase createUseCase;
  private UpdateMedicineUseCase updateUseCase;
  private DeactivateMedicineUseCase deactivateUseCase;
  private ReactivateMedicineUseCase reactivateUseCase;

  @BeforeEach
  void setUp() {
    createUseCase = new CreateMedicineUseCase(medicineRepository, accessPolicy, audit);
    updateUseCase = new UpdateMedicineUseCase(medicineRepository, accessPolicy, audit);
    deactivateUseCase = new DeactivateMedicineUseCase(medicineRepository, accessPolicy, audit);
    reactivateUseCase = new ReactivateMedicineUseCase(medicineRepository, accessPolicy, audit);
  }

  @Test
  void createMedicinePersistsAndAudits() {
    UserPrincipal principal = manager(CatalogAccessPolicy.MEDICINE_CREATE);
    CreateMedicineCommand cmd =
        new CreateMedicineCommand(
            "PARA-500", "Paracetamol", "Panadol", "500mg", "Viên nén", "Viên");

    org.mockito.Mockito.doAnswer(
            invocation -> {
              Medicine d = invocation.getArgument(0);
              Instant at = Instant.parse("2026-10-14T00:00:00Z");
              when(medicineRepository.findById(d.id()))
                  .thenReturn(
                      Optional.of(
                          Medicine.restore(
                              d.id(),
                              d.code(),
                              d.genericName(),
                              d.brandName(),
                              d.strength(),
                              d.dosageForm(),
                              d.unit(),
                              true,
                              at,
                              at,
                              0)));
              return null;
            })
        .when(medicineRepository)
        .save(any());
    MedicineResponse res = createUseCase.execute(cmd, principal);

    assertThat(res.code()).isEqualTo("PARA-500");
    assertThat(res.genericName()).isEqualTo("Paracetamol");
    assertThat(res.brandName()).isEqualTo("Panadol");
    assertThat(res.strength()).isEqualTo("500mg");
    assertThat(res.dosageForm()).isEqualTo("Viên nén");
    assertThat(res.unit()).isEqualTo("Viên");
    assertThat(res.active()).isTrue();
    assertThat(res.rowVersion()).isZero();

    verify(medicineRepository).save(any(Medicine.class));
    verify(audit)
        .record(eq(principal.userId()), eq("CREATE_MEDICINE"), eq("MEDICINE"), any(), any(), any());
  }

  @Test
  void updateMedicineThrowsOnRowVersionMismatch() {
    UserPrincipal principal = manager(CatalogAccessPolicy.MEDICINE_UPDATE);
    UUID id = UUID.randomUUID();
    Medicine med =
        Medicine.restore(
            id,
            new CatalogCode("PARA"),
            "Paracetamol",
            null,
            "500mg",
            "Viên nén",
            "Viên",
            true,
            Instant.now(),
            Instant.now(),
            2L);
    when(medicineRepository.findByIdForUpdate(id)).thenReturn(Optional.of(med));

    UpdateMedicineCommand cmd =
        new UpdateMedicineCommand(id, "Paracetamol", "Hapacol", "500mg", "Viên nén", "Viên", 1L);

    assertThatThrownBy(() -> updateUseCase.execute(cmd, principal))
        .isInstanceOf(ConcurrentUpdateException.class);

    verify(medicineRepository, never()).update(any(), any(Long.class));
  }
}
