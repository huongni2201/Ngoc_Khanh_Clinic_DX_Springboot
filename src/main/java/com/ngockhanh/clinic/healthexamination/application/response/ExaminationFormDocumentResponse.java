package com.ngockhanh.clinic.healthexamination.application.response;

/**
 * The rendered blank examination form of one Participant. The bytes are copied on construction and
 * on read.
 *
 * @param content PDF bytes
 * @param fileName download name; only the filtered participant code, never personal data
 */
public record ExaminationFormDocumentResponse(byte[] content, String fileName) {
  public ExaminationFormDocumentResponse {
    if (content == null || content.length == 0 || fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Document content and file name are required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
