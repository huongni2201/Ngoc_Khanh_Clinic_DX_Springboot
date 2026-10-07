package com.ngockhanh.clinic.healthexamination.application.response;

/**
 * The generated examination detail workbook. The bytes are copied on construction and on read.
 *
 * @param content XLSX bytes
 * @param fileName download name; only the filtered batch code, never personal data
 */
public record ExaminationDetailExportResponse(byte[] content, String fileName) {
  public ExaminationDetailExportResponse {
    if (content == null || content.length == 0 || fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Workbook content and file name are required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
