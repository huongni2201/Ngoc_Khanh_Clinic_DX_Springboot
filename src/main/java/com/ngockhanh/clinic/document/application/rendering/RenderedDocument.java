package com.ngockhanh.clinic.document.application.rendering;

import java.util.UUID;

/**
 * A rendered PDF and the template version that produced it.
 *
 * @param pdf the PDF bytes
 * @param templateVersionId version of the template that was used
 * @param templateVersionNo human-readable number of that version
 */
public record RenderedDocument(byte[] pdf, UUID templateVersionId, int templateVersionNo) {}
