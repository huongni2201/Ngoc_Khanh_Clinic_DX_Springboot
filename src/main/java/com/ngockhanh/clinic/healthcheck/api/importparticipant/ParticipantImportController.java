package com.ngockhanh.clinic.healthcheck.api.importparticipant;

import java.io.IOException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportPreview;
import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportUpload;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantTemplateWriter;
import com.ngockhanh.clinic.healthcheck.application.query.ParticipantSummary;
import com.ngockhanh.clinic.healthcheck.application.usecase.ConfirmParticipantImportUseCase;
import com.ngockhanh.clinic.healthcheck.application.usecase.GetParticipantImportPreviewUseCase;
import com.ngockhanh.clinic.healthcheck.application.usecase.ListBatchParticipantsUseCase;
import com.ngockhanh.clinic.healthcheck.application.usecase.StageParticipantImportUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}")
@RequiredArgsConstructor
public final class ParticipantImportController {
    private static final String TEMPLATE_FILENAME = "health-check-participants-template.xlsx";
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ParticipantTemplateWriter templateWriter;
    private final StageParticipantImportUseCase stageImport;
    private final GetParticipantImportPreviewUseCase getPreview;
    private final ConfirmParticipantImportUseCase confirmImport;
    private final ListBatchParticipantsUseCase listParticipants;


    @GetMapping("/participants")
    public ApiResponse<PageResponse<ParticipantSummary>> participants(
            @PathVariable UUID organizationId, @PathVariable UUID batchId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
        return new ApiResponse<>(200, "Health examination batch participants",
                listParticipants.execute(organizationId, batchId, page, size));
    }

    @GetMapping("/participants/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().contentType(XLSX).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + TEMPLATE_FILENAME + "\"")
                .body(templateWriter.generate());
    }

    @PostMapping(path = "/participant-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ParticipantImportPreview>> upload(
            @PathVariable UUID organizationId, @PathVariable UUID batchId,
            @RequestPart("file") MultipartFile file, Authentication authentication) throws IOException {

        ParticipantImportPreview preview = stageImport.execute(new ParticipantImportUpload(
                organizationId, batchId, currentUserId(authentication), file.getOriginalFilename(),
                file.getContentType(), file.getBytes()));
        return ResponseEntity.status(201).body(new ApiResponse<>(201, "Participant roster staged", preview));
    }

    @GetMapping("/participant-imports/{importId}")
    public ApiResponse<ParticipantImportPreview> preview(@PathVariable UUID organizationId,
                                                           @PathVariable UUID batchId,
                                                           @PathVariable UUID importId) {
        return new ApiResponse<>(200, "Participant import preview", getPreview.execute(organizationId, batchId, importId));
    }

    @PostMapping("/participant-imports/{importId}/confirm")
    public ApiResponse<ParticipantImportPreview> confirm(@PathVariable UUID organizationId,
                                                           @PathVariable UUID batchId,
                                                           @PathVariable UUID importId,
                                                           Authentication authentication) {
        return new ApiResponse<>(200, "Participant roster confirmed",
                confirmImport.execute(organizationId, batchId, importId, currentUserId(authentication)));
    }
}
