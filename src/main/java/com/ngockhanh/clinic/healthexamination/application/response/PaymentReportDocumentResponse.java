package com.ngockhanh.clinic.healthexamination.application.response;

/**
 * The generated payment summary document. The bytes are copied on construction and on read.
 *
 * @param content DOCX bytes
 * @param fileName download name; only the filtered batch code, never personal data
 */
public record PaymentReportDocumentResponse(byte[] content, String fileName) {
  public PaymentReportDocumentResponse {
    if (content == null || content.length == 0 || fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Document content and file name are required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
