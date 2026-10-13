package com.ngockhanh.clinic.document.application.response;

/**
 * One merged PDF part of a print job. The bytes are copied on construction and on read.
 *
 * @param content PDF bytes
 * @param fileName download name, never personal data
 */
public record RenderJobPartResponse(byte[] content, String fileName) {
  public RenderJobPartResponse {
    if (content == null || content.length == 0 || fileName == null || fileName.isBlank())
      throw new IllegalArgumentException("Document content and file name are required");
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
