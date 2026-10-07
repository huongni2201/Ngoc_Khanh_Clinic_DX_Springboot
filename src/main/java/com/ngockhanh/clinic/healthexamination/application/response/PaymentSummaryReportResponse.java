package com.ngockhanh.clinic.healthexamination.application.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Payment summary of one batch: for each batch service and price snapshot, how many active
 * Participants were recorded as performed, the price and the amount. The same object feeds the
 * screen, the JSON report and the Word document.
 *
 * @param provisional true while the batch is not FINALIZED, so the figures may still change
 * @param totalAmount sum of the item amounts
 */
@Builder
public record PaymentSummaryReportResponse(
    UUID batchId,
    String batchCode,
    String batchName,
    String batchStatus,
    boolean provisional,
    long registeredCount,
    long attendedCount,
    long reconciledCount,
    List<Item> items,
    BigDecimal totalAmount,
    Instant generatedAt) {
  public PaymentSummaryReportResponse {
    items = List.copyOf(items);
  }

  /**
   * One line of the report.
   *
   * @param serviceCode catalog code, or null when the catalog no longer knows the service
   * @param serviceName catalog name, or null when the catalog no longer knows the service
   * @param unitPrice historical price snapshot of the performed rows, or the current negotiated
   *     price for a service nobody performed
   * @param examinedCount number of active Participants it was performed for
   * @param amount {@code unitPrice × examinedCount}
   */
  @Builder
  public record Item(
      UUID batchServiceId,
      String serviceCode,
      String serviceName,
      int displayOrder,
      BigDecimal unitPrice,
      long examinedCount,
      BigDecimal amount) {}
}
