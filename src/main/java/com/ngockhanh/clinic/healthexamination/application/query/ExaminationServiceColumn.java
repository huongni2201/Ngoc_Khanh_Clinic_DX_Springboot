package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.UUID;

/**
 * One service column of the examination detail workbook.
 *
 * @param agreementServiceId agreement service the column records; it is the machine key of the
 *     column
 * @param label display name of the catalog service, never used to match columns on import
 */
public record ExaminationServiceColumn(UUID agreementServiceId, String label) {}
