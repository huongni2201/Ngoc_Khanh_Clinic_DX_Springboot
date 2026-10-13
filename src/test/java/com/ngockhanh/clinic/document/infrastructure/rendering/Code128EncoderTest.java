package com.ngockhanh.clinic.document.infrastructure.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The encoder was also checked against an independent decoder (zxing-cpp) on hundreds of random
 * strings; these tests pin the rules so a change cannot silently break the symbol.
 */
class Code128EncoderTest {
  @Test
  void encodesCodeSetBWithTheStandardCheckSymbol() {
    // start B (104) + 'A'(33)*1 + 'B'(34)*2 + 'C'(35)*3 = 310; 310 mod 103 = 1
    assertThat(Code128Encoder.symbols("ABC")).containsExactly(104, 33, 34, 35, 1);
    // space is value 0 and tilde is value 94
    assertThat(Code128Encoder.symbols(" ~")).first().isEqualTo(104);
    assertThat(Code128Encoder.symbols(" ~").subList(1, 3)).containsExactly(0, 94);
  }

  @Test
  void usesCodeSetCForEvenDigitRunsAndSwitchesBackForALeftoverDigit() {
    // start C (105), 01 23 45 67 89, check
    assertThat(Code128Encoder.symbols("0123456789")).containsExactly(105, 1, 23, 45, 67, 89, 73);
    // 12 34 in C, then CODE B (100) and '5'
    assertThat(Code128Encoder.symbols("12345")).containsExactly(105, 12, 34, 100, 21, 54);
  }

  @Test
  void switchesToCodeSetCOnlyWhenItMakesTheSymbolShorter() {
    // Four digits between letters cost as much in set B, so no switch is made.
    assertThat(Code128Encoder.symbols("a1234b")).doesNotContain(99, 105);
    // The four trailing digits of a participant code are shorter in set C.
    var code = Code128Encoder.symbols("KSK-2026-001-0001");
    assertThat(code).contains(99);
    assertThat(code).hasSize(18);
    assertThat(Code128Encoder.modules("KSK-2026-001-0001")).isEqualTo(211);
  }

  @Test
  void everyPatternHasElevenModulesAndTheStopHasThirteen() {
    // Each symbol is three bars and three spaces of 11 modules; the stop adds a final bar.
    int[] widths = Code128Encoder.widths("Hello, World! ~ 0123456789");
    int symbols = Code128Encoder.symbols("Hello, World! ~ 0123456789").size();
    int total = 0;
    for (int width : widths) total += width;

    assertThat(widths).hasSize(symbols * 6 + 7);
    assertThat(total).isEqualTo(symbols * 11 + 13);
    assertThat(widths[0]).isPositive();
  }

  @Test
  void theSymbolEndsWithTheStopPattern() {
    int[] widths = Code128Encoder.widths("A");

    assertThat(List.of(widths[widths.length - 7], widths[widths.length - 6]))
        .containsExactly(2, 3);
    assertThat(widths[widths.length - 1]).isEqualTo(2);
  }

  @Test
  void refusesEmptyTextAndCharactersOutsidePrintableAscii() {
    assertThatThrownBy(() -> Code128Encoder.widths("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Code128Encoder.widths(null))
        .isInstanceOf(IllegalArgumentException.class);
    for (String text : List.of("tab\there", "Nguyễn", "line\nbreak", "\u007f"))
      assertThatThrownBy(() -> Code128Encoder.widths(text))
          .as(text)
          .isInstanceOf(IllegalArgumentException.class);
  }
}
