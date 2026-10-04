package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportRowsRequest;
import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportUploadResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.DownloadParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantImportRowsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UploadParticipantImportUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ParticipantImportController {
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final DownloadParticipantImportTemplateUseCase downloadTemplate;
  private final UploadParticipantImportUseCase uploadImport;
  private final GetParticipantImportUseCase getImport;
  private final ListParticipantImportRowsUseCase listRows;

  /**
   * Downloads the standard batch participant roster template for an organization batch.
   *
   * @param organizationId organization identifier
   * @param batchId health examination batch identifier
   * @return the Excel template attachment
   */
  @GetMapping("/participants/export-template")
  public ResponseEntity<byte[]> downloadTemplate(
      @PathVariable UUID organizationId, @PathVariable UUID batchId) {
    byte[] content = downloadTemplate.execute(organizationId, batchId);
    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("participant-import-template.xlsx")
                .build()
                .toString())
        .body(content);
  }

  /**
   * Validates a standard roster workbook and stages it only when every row is valid.
   *
   * @param organizationId organization identifier
   * @param batchId health examination batch identifier
   * @param file uploaded Excel workbook
   * @param configuration selected examination days
   * @param authentication authenticated manager identity
   * @return the validated import preview or rejected row errors
   */
  @PostMapping(value = "/participant-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<ParticipantImportUploadResponse>> upload(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("configuration")
          com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportUploadRequest
              configuration,
      Authentication authentication) {
    try (InputStream content = file.getInputStream()) {
      ParticipantImportUploadResponse response =
          uploadImport.execute(
              new UploadParticipantImportCommand(
                  organizationId,
                  batchId,
                  actorId(authentication),
                  file.getOriginalFilename(),
                  file.getContentType(),
                  file.getSize(),
                  content,
                  configuration.selectedBatchDayIds()));
      HttpStatus status =
          response.importId() == null ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.CREATED;
      return ResponseEntity.status(status)
          .body(
              ApiResponse.success(
                  status.value(),
                  response.importId() == null
                      ? "Participant roster rejected"
                      : "Participant roster validated",
                  response));
    } catch (IOException failure) {
      throw new IllegalStateException("Unable to read participant roster upload", failure);
    }
  }

  /**
   * Retrieves the status, selected days and version for an import.
   *
   * @param organizationId organization identifier
   * @param batchId health examination batch identifier
   * @param importId participant import identifier
   * @return the import summary
   */
  @GetMapping("/participant-imports/{importId}")
  public ResponseEntity<ApiResponse<ParticipantImportSummaryResponse>> get(
      @PathVariable UUID organizationId, @PathVariable UUID batchId, @PathVariable UUID importId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), getImport.execute(organizationId, batchId, importId)));
  }

  /**
   * Retrieves a filtered page of import rows with masked identification numbers.
   *
   * @param organizationId organization identifier
   * @param batchId health examination batch identifier
   * @param importId participant import identifier
   * @param request page, size, and optional row filter
   * @return the requested import row page
   */
  @GetMapping("/participant-imports/{importId}/rows")
  public ResponseEntity<ApiResponse<ParticipantImportRowsPageResponse>> rows(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @PathVariable UUID importId,
      @Valid @ModelAttribute ParticipantImportRowsRequest request) {
    int page = request.page() == null ? 1 : request.page();
    int size = request.size() == null ? 50 : request.size();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            listRows.execute(organizationId, batchId, importId, page, size, request.status())));
  }

  private static UUID actorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new IllegalArgumentException("Authenticated manager identity is required");
    }
    try {
      return UUID.fromString(authentication.getName());
    } catch (IllegalArgumentException invalidIdentity) {
      throw new IllegalArgumentException(
          "Authenticated manager identity is invalid", invalidIdentity);
    }
  }
}
