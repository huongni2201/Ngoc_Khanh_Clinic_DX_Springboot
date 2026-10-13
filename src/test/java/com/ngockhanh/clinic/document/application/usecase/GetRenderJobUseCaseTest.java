package com.ngockhanh.clinic.document.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.service.RenderJobAccessPolicy;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetRenderJobUseCaseTest {
  private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");

  private final RenderJobFakes.InMemoryStore store = new RenderJobFakes.InMemoryStore();
  private final GetRenderJobUseCase useCase = new GetRenderJobUseCase(new RenderJobAccessPolicy(), store);
  private final UserPrincipal creator = RenderJobFakes.staff(RenderJobAccessPolicy.READ_PERMISSION);

  private UUID job(UUID requester) {
    UUID id = UUID.randomUUID();
    store.insert(
        id,
        "T",
        requester,
        UUID.randomUUID(),
        Map.of("batchId", "b"),
        List.of(UUID.randomUUID(), UUID.randomUUID()),
        START);
    return id;
  }

  @Test
  void showsProgressToTheAccountThatCreatedTheJob() {
    var id = job(creator.userId());

    var response = useCase.execute(id, creator);

    assertThat(response.id()).isEqualTo(id);
    assertThat(response.status()).isEqualTo("QUEUED");
    assertThat(response.totalCount()).isEqualTo(2);
    assertThat(response.processedCount()).isZero();
    assertThat(response.parts()).isEmpty();
    assertThat(response.skipped()).isEmpty();
  }

  @Test
  void listsSkippedRecordsAndPartsOnceTheJobSucceeded() {
    var id = job(creator.userId());
    var skippedId = UUID.randomUUID();
    var claimed = store.claimNext(START, START.plus(Duration.ofMinutes(5)), 3).orElseThrow();
    store.addPart(
        id,
        claimed.lockToken(),
        1,
        2,
        new NewFile(UUID.randomUUID(), "LOCAL", "k.pdf", "part-1.pdf", "application/pdf", 1234, new byte[32]),
        START.plus(Duration.ofMinutes(5)));
    store.finish(
        id,
        claimed.lockToken(),
        true,
        null,
        2,
        List.of(new DocumentValuesSource.Skipped(skippedId, "CANCELLED")),
        START,
        START.plus(Duration.ofHours(24)));

    var response = useCase.execute(id, creator);

    assertThat(response.status()).isEqualTo("SUCCEEDED");
    assertThat(response.skipped())
        .extracting(s -> s.id() + ":" + s.reason())
        .containsExactly(skippedId + ":CANCELLED");
    assertThat(response.parts()).hasSize(1);
    assertThat(response.parts().get(0).partNo()).isEqualTo(1);
    assertThat(response.parts().get(0).recordCount()).isEqualTo(2);
    assertThat(response.parts().get(0).sizeBytes()).isEqualTo(1234);
    assertThat(response.expiresAt()).isEqualTo(START.plus(Duration.ofHours(24)));
  }

  @Test
  void anotherAccountSeesNothingEvenWithTheSamePermission() {
    var id = job(creator.userId());
    var stranger = RenderJobFakes.staff(RenderJobAccessPolicy.READ_PERMISSION);

    assertThatThrownBy(() -> useCase.execute(id, stranger)).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), creator))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void requiresThePermissionAndAnIdentifier() {
    var id = job(creator.userId());
    var withoutPermission = RenderJobFakes.staff(RenderJobAccessPolicy.DOWNLOAD_PERMISSION);

    assertThatThrownBy(() -> useCase.execute(id, withoutPermission))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(() -> useCase.execute(id, null)).isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> useCase.execute(null, creator)).isInstanceOf(IllegalArgumentException.class);
  }
}
