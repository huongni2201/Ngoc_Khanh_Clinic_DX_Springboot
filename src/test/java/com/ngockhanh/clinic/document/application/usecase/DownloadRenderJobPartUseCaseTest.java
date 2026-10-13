package com.ngockhanh.clinic.document.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.rendering.RenderJobs;
import com.ngockhanh.clinic.document.application.service.RenderJobAccessPolicy;
import com.ngockhanh.clinic.document.application.service.RenderJobFakes;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownloadRenderJobPartUseCaseTest {
  private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");

  private final RenderJobFakes.InMemoryStore store = new RenderJobFakes.InMemoryStore();
  private final RenderJobFakes.InMemoryStorage storage = new RenderJobFakes.InMemoryStorage();
  private final RenderJobFakes.RecordingAudit audit = new RenderJobFakes.RecordingAudit();
  private final DownloadRenderJobPartUseCase useCase =
      new DownloadRenderJobPartUseCase(new RenderJobAccessPolicy(), store, storage, audit);
  private final UserPrincipal creator =
      RenderJobFakes.staff(RenderJobAccessPolicy.DOWNLOAD_PERMISSION);
  private final byte[] pdf = "%PDF-1.7 synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  /** A job of the creator, finished successfully with one stored part. */
  private UUID succeededJob(Map<String, String> context) {
    UUID id = UUID.randomUUID();
    store.insert(id, "T", creator.userId(), UUID.randomUUID(), context, List.of(UUID.randomUUID()), START);
    var claimed = store.claimNext(START, START.plus(Duration.ofMinutes(5)), 3).orElseThrow();
    String key = "render-jobs/2026/10/" + id + "-1.pdf";
    storage.files.put("LOCAL:" + key, pdf);
    store.addPart(
        id,
        claimed.lockToken(),
        1,
        1,
        new NewFile(UUID.randomUUID(), "LOCAL", key, "part-1.pdf", "application/pdf", pdf.length, new byte[32]),
        START.plus(Duration.ofMinutes(5)));
    store.finish(id, claimed.lockToken(), true, null, 1, List.of(), START, START.plus(Duration.ofHours(24)));
    return id;
  }

  @Test
  void givesTheCreatorThePartNamedAfterTheJobAndAuditsIt() {
    var id = succeededJob(Map.of(RenderJobs.FILE_NAME_KEY, "phieu-kham-KSK-1.pdf"));

    var file = useCase.execute(id, 1, creator);

    assertThat(file.content()).isEqualTo(pdf);
    assertThat(file.fileName()).isEqualTo("phieu-kham-KSK-1-phan-1.pdf");
    assertThat(audit.events).hasSize(1);
    var event = audit.events.get(0);
    assertThat(event.actor()).isEqualTo(creator.userId());
    assertThat(event.action()).isEqualTo("DOWNLOAD_RENDER_JOB_PART");
    assertThat(event.entityType()).isEqualTo("DOCUMENT_RENDER_JOB");
    assertThat(event.entityId()).isEqualTo(id);
    assertThat(event.after()).isEqualTo(Map.of("partNo", 1, "documentType", "T"));
  }

  @Test
  void usesAGenericNameWhenTheCreatorGaveNone() {
    var id = succeededJob(Map.of());

    assertThat(useCase.execute(id, 1, creator).fileName()).isEqualTo("documents-phan-1.pdf");
  }

  @Test
  void anotherAccountGetsNotFoundEvenWithThePermission() {
    var id = succeededJob(Map.of());
    var stranger = RenderJobFakes.staff(RenderJobAccessPolicy.DOWNLOAD_PERMISSION);

    assertThatThrownBy(() -> useCase.execute(id, 1, stranger)).isInstanceOf(ResourceNotFoundException.class);
    assertThat(audit.events).isEmpty();
  }

  @Test
  void anUnknownPartOrJobIsNotFound() {
    var id = succeededJob(Map.of());

    assertThatThrownBy(() -> useCase.execute(id, 2, creator)).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), 1, creator))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void anExpiredJobIsGone() {
    var id = succeededJob(Map.of());
    store.row(id).status = "EXPIRED";

    assertThatThrownBy(() -> useCase.execute(id, 1, creator)).isInstanceOf(ResourceNotFoundException.class);
    assertThat(audit.events).isEmpty();
  }

  @Test
  void aJobThatDidNotSucceedHasNothingToDownload() {
    UUID id = UUID.randomUUID();
    store.insert(id, "T", creator.userId(), UUID.randomUUID(), Map.of(), List.of(UUID.randomUUID()), START);

    assertThatThrownBy(() -> useCase.execute(id, 1, creator)).isInstanceOf(ConflictException.class);
    store.row(id).status = "FAILED";
    assertThatThrownBy(() -> useCase.execute(id, 1, creator)).isInstanceOf(ConflictException.class);
  }

  @Test
  void requiresThePermissionAndValidInput() {
    var id = succeededJob(Map.of());
    var readOnly = RenderJobFakes.staff(RenderJobAccessPolicy.READ_PERMISSION);

    assertThatThrownBy(() -> useCase.execute(id, 1, readOnly))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(() -> useCase.execute(null, 1, creator)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute(id, 0, creator)).isInstanceOf(IllegalArgumentException.class);
    assertThat(audit.events).isEmpty();
  }
}
