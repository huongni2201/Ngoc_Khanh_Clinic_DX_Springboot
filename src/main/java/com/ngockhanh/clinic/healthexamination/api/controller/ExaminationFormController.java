package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.RenderParticipantExaminationFormUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Prints the blank examination form of one Participant of a health examination batch. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/{participantId}")
public class ExaminationFormController {
  private final RenderParticipantExaminationFormUseCase useCase;

  /**
   * Returns the form as a PDF the browser can show and print.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param participantId Participant identifier inside the batch
   * @param principal authenticated staff principal
   * @return 200 with the PDF inline and {@code no-store}, since it holds personal data
   */
  @GetMapping("/examination-form")
  public ResponseEntity<byte[]> examinationForm(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID participantId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Examination form request: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    ExaminationFormDocumentResponse file =
        useCase.execute(organizationId, batchId, participantId, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }
}
