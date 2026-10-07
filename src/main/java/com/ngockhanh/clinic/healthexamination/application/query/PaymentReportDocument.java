package com.ngockhanh.clinic.healthexamination.application.query;

import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import java.time.LocalDate;

/**
 * Input of the payment summary Word writer: the report numbers, which come from the same read model
 * as the JSON report, and the header details of the organization and the batch.
 *
 * @param organizationTaxCode tax code of the organization, or null when it has none
 */
public record PaymentReportDocument(
    PaymentSummaryReportResponse report,
    String organizationName,
    String organizationTaxCode,
    String organizationAddress,
    LocalDate startDate,
    LocalDate endDate,
    String siteName,
    String siteAddress) {}
