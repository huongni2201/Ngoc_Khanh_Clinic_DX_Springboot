package com.ngockhanh.clinic.healthexamination.api.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportMappingRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportRowsRequest;
import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportConfirmResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportUploadResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ConfirmParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DownloadParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantImportRowsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UploadParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ValidateParticipantImportUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}")
@RequiredArgsConstructor
public class ParticipantImportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DownloadParticipantImportTemplateUseCase downloadTemplate;
    private final UploadParticipantImportUseCase uploadImport;
    private final ValidateParticipantImportUseCase validateImport;
    private final GetParticipantImportUseCase getImport;
    private final ListParticipantImportRowsUseCase listRows;
    private final ConfirmParticipantImportUseCase confirmImport;
    private final CancelParticipantImportUseCase cancelImport;

    /**
     * Downloads the blank 16-column participant roster template for an organization batch.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @return the Excel template attachment
     */
    @GetMapping("/employees/import-template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID organizationId,
                                                   @PathVariable UUID batchId) {
        byte[] content = downloadTemplate.execute(organizationId, batchId);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("employee-import-template.xlsx").build().toString())
                .body(content);
    }

    /**
     * Uploads a roster workbook and returns its headers with a suggested column mapping.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @param file uploaded Excel workbook
     * @param authentication authenticated manager identity
     * @return the staged import identifier and detected headers
     */
    @PostMapping(value = "/employee-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ParticipantImportUploadResponse>> upload(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try (InputStream content = file.getInputStream()) {
            ParticipantImportUploadResponse response = uploadImport.execute(new UploadParticipantImportCommand(
                    organizationId, batchId, actorId(authentication), file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), content));
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    HttpStatus.CREATED.value(), "Participant roster uploaded", response));
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read participant roster upload", failure);
        }
    }

    /**
     * Saves the selected workbook columns and validates all roster rows.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @param importId participant import identifier
     * @param request selected workbook column indexes
     * @param authentication authenticated manager identity
     * @return row counts, validation results, and confirm eligibility
     */
    @PutMapping("/employee-imports/{importId}/mapping")
    public ResponseEntity<ApiResponse<ParticipantImportSummaryResponse>> validate(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @PathVariable UUID importId,
            @Valid @RequestBody ParticipantImportMappingRequest request,
            Authentication authentication) {
        ParticipantImportSummaryResponse response = validateImport.execute(organizationId, batchId, importId,
                actorId(authentication), request.columns());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Participant roster validated", response));
    }

    /**
     * Retrieves the status, selected mapping, headers, and validation counts for an import.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @param importId participant import identifier
     * @return the import summary
     */
    @GetMapping("/employee-imports/{importId}")
    public ResponseEntity<ApiResponse<ParticipantImportSummaryResponse>> get(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @PathVariable UUID importId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                getImport.execute(organizationId, batchId, importId)));
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
    @GetMapping("/employee-imports/{importId}/rows")
    public ResponseEntity<ApiResponse<ParticipantImportRowsPageResponse>> rows(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @PathVariable UUID importId,
            @Valid @ModelAttribute ParticipantImportRowsRequest request) {
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 50 : request.size();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), listRows.execute(
                organizationId, batchId, importId, page, size, request.status())));
    }

    /**
     * Confirms a validated roster as one atomic import operation.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @param importId participant import identifier
     * @param authentication authenticated manager identity
     * @return final import counts, including created, updated, and unchanged rows
     */
    @PostMapping("/employee-imports/{importId}/confirm")
    public ResponseEntity<ApiResponse<ParticipantImportConfirmResponse>> confirm(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @PathVariable UUID importId,
            Authentication authentication) {
        ParticipantImportConfirmResponse response = confirmImport.execute(
                organizationId, batchId, importId, actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Participant roster confirmed", response));
    }

    /**
     * Cancels an uploaded or validated participant roster import and retains its audit history.
     *
     * @param organizationId organization identifier
     * @param batchId health examination batch identifier
     * @param importId participant import identifier
     * @param authentication authenticated manager identity
     * @return the canceled import summary
     */
    @DeleteMapping("/employee-imports/{importId}")
    public ResponseEntity<ApiResponse<ParticipantImportSummaryResponse>> cancel(
            @PathVariable UUID organizationId,
            @PathVariable UUID batchId,
            @PathVariable UUID importId,
            Authentication authentication) {
        ParticipantImportSummaryResponse response = cancelImport.execute(
                organizationId, batchId, importId, actorId(authentication));
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Participant import canceled", response));
    }

    private static UUID actorId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authenticated manager identity is required");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException invalidIdentity) {
            throw new IllegalArgumentException("Authenticated manager identity is invalid", invalidIdentity);
        }
    }
}
