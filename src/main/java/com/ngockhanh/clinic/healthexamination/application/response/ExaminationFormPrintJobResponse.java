package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.UUID;

/**
 * A queued print job.
 *
 * @param jobId identifier to poll at {@code GET /api/v1/document-render-jobs/{jobId}}
 */
public record ExaminationFormPrintJobResponse(UUID jobId) {}
