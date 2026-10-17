package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentReportDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report endpoints of one health examination agreement: the payment summary and its Word export.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/agreements/{agreementId}/reports")
public class AgreementReportController {
  /** Media type of a Word {@code .docx} document. */
  public static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final GetPaymentSummaryReportUseCase summaryUseCase;
  private final ExportPaymentSummaryReportUseCase exportUseCase;

  /**
   * Returns the payment summary of the agreement.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param from first session date of the optional range
   * @param to last session date of the optional range
   * @param principal authenticated staff principal
   * @return 200 with one item per service and price snapshot, counts and the total
   */
  @GetMapping("/payment-summary")
  public ResponseEntity<ApiResponse<PaymentSummaryReportResponse>> paymentSummary(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Payment summary request: organizationId={}, agreementId={}", organizationId, agreementId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                summaryUseCase.execute(organizationId, agreementId, from, to, principal)));
  }

  /**
   * Downloads the payment summary as a Word document.
   *
   * @return the DOCX bytes as an attachment
   */
  @GetMapping("/payment-summary/docx")
  public ResponseEntity<byte[]> paymentSummaryDocx(
      @PathVariable UUID organizationId,
      @PathVariable UUID agreementId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Payment summary document request: organizationId={}, agreementId={}",
        organizationId,
        agreementId);
    PaymentReportDocumentResponse file =
        exportUseCase.execute(organizationId, agreementId, from, to, principal);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(DOCX_CONTENT_TYPE))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .cacheControl(CacheControl.noStore())
        .body(file.content());
  }
}
