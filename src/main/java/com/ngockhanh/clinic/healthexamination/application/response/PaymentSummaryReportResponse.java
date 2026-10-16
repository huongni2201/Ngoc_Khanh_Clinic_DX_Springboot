package com.ngockhanh.clinic.healthexamination.application.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Payment summary of one agreement: for each agreement service and price snapshot, how many active
 * Participants were recorded as performed, the price and the amount. The same object feeds the
 * screen, the JSON report and the Word document.
 *
 * @param validFrom first day the agreement applies, or null
 * @param validTo last day the agreement applies, or null
 * @param rangeFrom first session date the report was limited to, or null
 * @param rangeTo last session date the report was limited to, or null
 * @param provisional true while the agreement has not ENDED, so the figures may still change
 * @param registeredCount Participants in scope
 * @param attendedCount distinct Participants who attended at least one session
 * @param sessionCount distinct sessions the Participants in scope are in
 * @param visitCount attended assignments; a Participant who came on two days counts twice
 * @param totalAmount sum of the item amounts
 */
@Builder
public record PaymentSummaryReportResponse(
    UUID agreementId,
    String agreementCode,
    String agreementName,
    String agreementStatus,
    LocalDate validFrom,
    LocalDate validTo,
    LocalDate rangeFrom,
    LocalDate rangeTo,
    boolean provisional,
    long registeredCount,
    long attendedCount,
    long reconciledCount,
    long sessionCount,
    long visitCount,
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
      UUID agreementServiceId,
      String serviceCode,
      String serviceName,
      int displayOrder,
      BigDecimal unitPrice,
      long examinedCount,
      BigDecimal amount) {}
}
