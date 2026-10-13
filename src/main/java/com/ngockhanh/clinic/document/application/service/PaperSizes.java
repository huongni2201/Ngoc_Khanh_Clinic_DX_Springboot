package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.port.PageLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Names the paper size and orientation of a Word page for {@code template_versions}. */
public final class PaperSizes {
  /** Largest gap in millimetres between a page edge and a standard paper edge. */
  private static final double TOLERANCE_MM = 1.5;

  private PaperSizes() {}

  /**
   * Result of {@link #classify}.
   *
   * @param paperSize {@code A3}, {@code A4}, {@code A5}, {@code A6} or {@code CUSTOM}
   * @param orientation {@code PORTRAIT} or {@code LANDSCAPE}
   * @param widthMm page width, one decimal
   * @param heightMm page height, one decimal
   */
  public record Classified(
      String paperSize, String orientation, BigDecimal widthMm, BigDecimal heightMm) {}

  public static Classified classify(PageLayout layout) {
    double shortEdge = Math.min(layout.widthMm(), layout.heightMm());
    double longEdge = Math.max(layout.widthMm(), layout.heightMm());
    return new Classified(
        name(shortEdge, longEdge),
        layout.landscape() ? "LANDSCAPE" : "PORTRAIT",
        BigDecimal.valueOf(layout.widthMm()).setScale(1, RoundingMode.HALF_UP),
        BigDecimal.valueOf(layout.heightMm()).setScale(1, RoundingMode.HALF_UP));
  }

  private static String name(double shortEdge, double longEdge) {
    if (matches(shortEdge, longEdge, 297, 420)) return "A3";
    if (matches(shortEdge, longEdge, 210, 297)) return "A4";
    if (matches(shortEdge, longEdge, 148, 210)) return "A5";
    if (matches(shortEdge, longEdge, 105, 148)) return "A6";
    return "CUSTOM";
  }

  private static boolean matches(double shortEdge, double longEdge, double shortMm, double longMm) {
    return Math.abs(shortEdge - shortMm) <= TOLERANCE_MM
        && Math.abs(longEdge - longMm) <= TOLERANCE_MM;
  }
}
