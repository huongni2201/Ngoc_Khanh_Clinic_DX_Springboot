package com.ngockhanh.clinic.document.infrastructure.rendering;

import com.ngockhanh.clinic.document.application.port.BarcodeImageGenerator;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Draws Code 128 as a 1-bit PNG with {@link Code128Encoder}, without any imaging library beyond the
 * JDK. The image has the pixel size of the template frame at {@value #DPI} dpi, so Word and
 * LibreOffice place it 1:1 instead of resampling. The bar width is the largest whole number of
 * pixels that lets the symbol and its quiet zones fit the frame width; the leftover width goes into
 * the quiet zones.
 */
public class Code128BarcodeGenerator implements BarcodeImageGenerator {
  static final int DPI = 1200;
  /** Quiet zone each side, in modules (the standard asks for at least ten). */
  static final int QUIET_ZONE_MODULES = 10;
  /** A bar thinner than this does not scan reliably on an office printer. */
  static final double MIN_MODULE_MM = 0.17;

  private static final double PIXELS_PER_MM = DPI / 25.4;
  private static final double MAX_FRAME_MM = 300;

  @Override
  public byte[] code128Png(String text, double widthMm, double heightMm) {
    if (!(widthMm > 0 && widthMm <= MAX_FRAME_MM && heightMm > 0 && heightMm <= MAX_FRAME_MM))
      throw new IllegalArgumentException("The barcode frame has an unusable size");
    int[] widths;
    try {
      widths = Code128Encoder.widths(text);
    } catch (IllegalArgumentException e) {
      throw new ConflictException("The value cannot be drawn as a barcode");
    }
    int modules = 0;
    for (int width : widths) modules += width;

    int widthPx = (int) Math.round(widthMm * PIXELS_PER_MM);
    int heightPx = (int) Math.round(heightMm * PIXELS_PER_MM);
    int module = widthPx / (modules + 2 * QUIET_ZONE_MODULES);
    if (module / PIXELS_PER_MM < MIN_MODULE_MM)
      throw new ConflictException(
          "The barcode does not fit its frame in the template; widen the frame or shorten the code");

    BufferedImage image = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_BYTE_BINARY);
    byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    int stride = (widthPx + 7) / 8;
    // Palette index 1 is white and 0 is black: a bar clears its bits.
    byte[] row = new byte[stride];
    Arrays.fill(row, (byte) 0xFF);
    int x = (widthPx - modules * module) / 2;
    boolean bar = true;
    for (int width : widths) {
      int end = x + width * module;
      if (bar) for (int px = x; px < end; px++) row[px >> 3] &= (byte) ~(0x80 >> (px & 7));
      x = end;
      bar = !bar;
    }
    for (int y = 0; y < heightPx; y++) System.arraycopy(row, 0, pixels, y * stride, stride);

    ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
    try {
      if (!ImageIO.write(image, "png", out)) throw new IllegalStateException("No PNG writer");
    } catch (IOException e) {
      throw new IllegalStateException("The barcode image could not be written", e);
    }
    return out.toByteArray();
  }
}
