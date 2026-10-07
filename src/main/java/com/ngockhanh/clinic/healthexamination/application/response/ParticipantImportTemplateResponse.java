package com.ngockhanh.clinic.healthexamination.application.response;

/**
 * The generated XLSX template. The bytes are copied on construction and on read.
 *
 * @param content XLSX bytes
 * @param fileName fixed download name; it never contains data of the batch
 */
public record ParticipantImportTemplateResponse(byte[] content, String fileName) {
  public ParticipantImportTemplateResponse {
    if (content == null || content.length == 0 || fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Template content and file name are required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
