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
import com.ngockhanh.clinic.catalog.application.command.CreateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.response.SpecialtyResponse;
import com.ngockhanh.clinic.catalog.application.service.CatalogAccessPolicy;
import com.ngockhanh.clinic.catalog.domain.aggregate.Specialty;
import com.ngockhanh.clinic.catalog.domain.repository.SpecialtyRepository;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecialtyUseCasesTest {
  private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
  private final CatalogAccessPolicy accessPolicy = new CatalogAccessPolicy();
  private final AuditWriter audit = mock(AuditWriter.class);

  private CreateSpecialtyUseCase createUseCase;
  private UpdateSpecialtyUseCase updateUseCase;
  private DeactivateSpecialtyUseCase deactivateUseCase;
  private ReactivateSpecialtyUseCase reactivateUseCase;

  @BeforeEach
  void setUp() {
    createUseCase = new CreateSpecialtyUseCase(specialtyRepository, accessPolicy, audit);
    updateUseCase = new UpdateSpecialtyUseCase(specialtyRepository, accessPolicy, audit);
    deactivateUseCase = new DeactivateSpecialtyUseCase(specialtyRepository, accessPolicy, audit);
    reactivateUseCase = new ReactivateSpecialtyUseCase(specialtyRepository, accessPolicy, audit);
  }

  @Test
  void deactivateSpecialtyThrowsConflictWhenActiveServicesExist() {
    UserPrincipal principal = manager(CatalogAccessPolicy.SPECIALTY_DEACTIVATE);
    UUID id = UUID.randomUUID();
    Specialty existing =
        Specialty.restore(
            id, new CatalogCode("NOI"), "Nội Khoa", true, Instant.now(), Instant.now(), 0L);
    when(specialtyRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
    when(specialtyRepository.countActiveServices(id)).thenReturn(3L);

    DeactivateSpecialtyCommand cmd = new DeactivateSpecialtyCommand(id, 0L);

    assertThatThrownBy(() -> deactivateUseCase.execute(cmd, principal))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Cannot deactivate specialty with 3 active service(s)");

    verify(specialtyRepository, never()).update(any(), any(Long.class));
  }

  @Test
  void createSpecialtyPersistsAndAudits() {
    UserPrincipal principal = manager(CatalogAccessPolicy.SPECIALTY_CREATE);
    CreateSpecialtyCommand cmd = new CreateSpecialtyCommand("NGOAI", "Ngoại Khoa");

    org.mockito.Mockito.doAnswer(
            invocation -> {
              Specialty d = invocation.getArgument(0);
              Instant at = Instant.parse("2026-10-14T00:00:00Z");
              when(specialtyRepository.findById(d.id()))
                  .thenReturn(
                      Optional.of(Specialty.restore(d.id(), d.code(), d.name(), true, at, at, 0)));
              return null;
            })
        .when(specialtyRepository)
        .save(any());
    SpecialtyResponse res = createUseCase.execute(cmd, principal);

    assertThat(res.code()).isEqualTo("NGOAI");
    assertThat(res.name()).isEqualTo("Ngoại Khoa");
    assertThat(res.active()).isTrue();
    assertThat(res.rowVersion()).isZero();

    verify(specialtyRepository).save(any(Specialty.class));
    verify(audit)
        .record(
            eq(principal.userId()), eq("CREATE_SPECIALTY"), eq("SPECIALTY"), any(), any(), any());
  }
}
