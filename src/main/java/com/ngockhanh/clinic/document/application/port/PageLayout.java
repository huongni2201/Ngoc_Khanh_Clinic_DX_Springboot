package com.ngockhanh.clinic.document.application.port;

/**
 * Page size of a Word document in millimetres, as written in its section settings.
 *
 * @param widthMm page width
 * @param heightMm page height; a width larger than the height means landscape
 */
public record PageLayout(double widthMm, double heightMm) {
  public boolean landscape() {
    return widthMm > heightMm;
  }
}
