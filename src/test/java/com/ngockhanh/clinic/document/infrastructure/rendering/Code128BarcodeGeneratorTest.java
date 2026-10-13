package com.ngockhanh.clinic.document.infrastructure.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Code128BarcodeGeneratorTest {
  private static final String CODE = "KSK-2026-001-0001";

  private final Code128BarcodeGenerator generator = new Code128BarcodeGenerator();

  @Test
  void drawsTheExactPixelSizeOfTheFrameAsAOneBitPng() throws Exception {
    BufferedImage image = read(generator.code128Png(CODE, 48.9, 9.0));

    // 1200 dpi: 48.9 mm = 2310 px, 9 mm = 425 px
    assertThat(image.getWidth()).isEqualTo(2310);
    assertThat(image.getHeight()).isEqualTo(425);
    assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_BINARY);
  }

  @Test
  void everyBarIsAWholeNumberOfPixelsAndTheQuietZonesAreAtLeastTenModules() throws Exception {
    BufferedImage image = read(generator.code128Png(CODE, 48.9, 9.0));
    // 211 modules + 2 * 10 quiet modules = 231; 2310 / 231 = 10 px per module (0.212 mm)
    int module = 10;

    List<Integer> runs = runs(image, 100);

    assertThat(runs.get(0)).isGreaterThanOrEqualTo(10 * module);
    assertThat(runs.get(runs.size() - 1)).isGreaterThanOrEqualTo(10 * module);
    for (int run : runs.subList(1, runs.size() - 1))
      assertThat(run % module).as("run of %d px", run).isZero();
    int symbolWidth = runs.subList(1, runs.size() - 1).stream().mapToInt(Integer::intValue).sum();
    assertThat(symbolWidth).isEqualTo(Code128Encoder.modules(CODE) * module);
  }

  @Test
  void theBarsRunTheWholeHeightOfTheFrame() throws Exception {
    BufferedImage image = read(generator.code128Png(CODE, 48.9, 9.0));

    assertThat(runs(image, 0)).isEqualTo(runs(image, image.getHeight() - 1));
  }

  @Test
  void aLongerCodeGetsThinnerBarsButStillWholePixels() throws Exception {
    // 18 characters: 222 + 20 modules in 2310 px = 9 px per module (0.19 mm), still scannable
    BufferedImage image = read(generator.code128Png("KSK-2026-001-00010", 48.9, 9.0));

    for (int run : runs(image, 100).subList(1, runs(image, 100).size() - 1))
      assertThat(run % 9).isZero();
  }

  @Test
  void aFrameThatIsTooNarrowIsAConflictNotAnUnreadableBarcode() {
    assertThatThrownBy(() -> generator.code128Png(CODE, 30, 9))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void textThatCannotBeEncodedIsAConflict() {
    assertThatThrownBy(() -> generator.code128Png("Nguyễn", 48.9, 9))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> generator.code128Png("", 48.9, 9))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> generator.code128Png(null, 48.9, 9))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void anUnusableFrameSizeIsAProgrammingOrTemplateFault() {
    for (double[] size : new double[][] {{0, 9}, {48.9, 0}, {-1, 9}, {48.9, Double.NaN}, {1000, 9}})
      assertThatThrownBy(() -> generator.code128Png(CODE, size[0], size[1]))
          .isInstanceOf(IllegalArgumentException.class);
  }

  private static BufferedImage read(byte[] png) throws Exception {
    assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    return ImageIO.read(new ByteArrayInputStream(png));
  }

  /** Lengths of the alternating white and black runs of one pixel row, starting with white. */
  private static List<Integer> runs(BufferedImage image, int row) {
    List<Integer> runs = new ArrayList<>();
    boolean black = false;
    int length = 0;
    for (int x = 0; x < image.getWidth(); x++) {
      boolean isBlack = (image.getRGB(x, row) & 0xFFFFFF) == 0;
      if (isBlack != black) {
        runs.add(length);
        length = 0;
        black = isBlack;
      }
      length++;
    }
    runs.add(length);
    return runs;
  }
}
