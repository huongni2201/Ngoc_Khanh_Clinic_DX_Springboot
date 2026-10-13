package com.ngockhanh.clinic.document.application.port;

/** Draws the Code 128 barcode image that replaces an image placeholder of a Word template. */
public interface BarcodeImageGenerator {
  /**
   * Draws {@code text} as a Code 128 barcode on a PNG that is exactly as large as the frame it
   * replaces, so the template never stretches it. Bars are a whole number of pixels wide.
   *
   * @param text printable ASCII (space to tilde) to encode
   * @param widthMm width of the frame in the template, in millimetres
   * @param heightMm height of the frame in the template, in millimetres
   * @return PNG bytes
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the text cannot be encoded
   *     or the bars would be too thin to scan in this frame
   */
  byte[] code128Png(String text, double widthMm, double heightMm);
}
