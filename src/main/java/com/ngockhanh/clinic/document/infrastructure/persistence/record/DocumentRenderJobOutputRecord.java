package com.ngockhanh.clinic.document.infrastructure.persistence.record;

import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.document_render_job_outputs}. */
@Builder
public record DocumentRenderJobOutputRecord(UUID jobId, int partNo, UUID fileId, int recordCount) {}
