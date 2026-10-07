package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;

/**
 * Renders the payment summary of a batch as a Word document. The document holds no personal data of
 * any Participant.
 */
public interface PaymentReportDocumentWriter {
  /** Returns the bytes of the DOCX document. */
  byte[] write(PaymentReportDocument document);
}
