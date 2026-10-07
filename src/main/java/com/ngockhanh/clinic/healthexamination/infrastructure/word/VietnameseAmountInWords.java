package com.ngockhanh.clinic.healthexamination.infrastructure.word;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reads a Vietnamese dong amount in words, for the "Bằng chữ" line of the payment summary.
 *
 * <p>Whole amounts end with "đồng chẵn". An amount with a fractional part reads the whole dong,
 * then the hundredths as "xu". Amounts are rounded to two decimals first. The reading follows the
 * common accounting form: "linh" for an empty tens place ("một trăm linh năm"), "mốt" after
 * "mươi", "lăm" after a non-empty tens place, and "không trăm" for an empty hundreds place of a
 * lower group ("một triệu không trăm linh năm nghìn").
 */
public final class VietnameseAmountInWords {
  private static final String[] DIGITS = {
    "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
  };
  // Index = group position counted from the right, one group = three digits.
  private static final String[] GROUP_UNITS = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"};

  private VietnameseAmountInWords() {}

  /**
   * Returns the amount in words, first letter capitalised.
   *
   * @param amount a non-negative amount in dong
   * @return for example {@code Một triệu không trăm linh năm nghìn đồng chẵn}
   * @throws IllegalArgumentException when the amount is null, negative or too large to read
   */
  public static String of(BigDecimal amount) {
    if (amount == null || amount.signum() < 0)
      throw new IllegalArgumentException("Amount must not be negative");
    BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
    var whole = rounded.toBigInteger();
    int hundredths = rounded.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
    if (whole.bitLength() > 62)
      throw new IllegalArgumentException("Amount is too large to read in words");
    String text = readWhole(whole.longValueExact()) + " đồng";
    text += hundredths == 0 ? " chẵn" : " và " + readBelowThousand(hundredths, false) + " xu";
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private static String readWhole(long value) {
    if (value == 0) return DIGITS[0];
    int[] groups = new int[GROUP_UNITS.length];
    int highest = -1;
    long rest = value;
    for (int i = 0; i < groups.length && rest > 0; i++) {
      groups[i] = (int) (rest % 1000);
      rest /= 1000;
      highest = i;
    }
    if (rest > 0) throw new IllegalArgumentException("Amount is too large to read in words");
    StringBuilder out = new StringBuilder();
    for (int i = highest; i >= 0; i--) {
      if (groups[i] == 0) continue;
      boolean lower = i < highest;
      if (!out.isEmpty()) out.append(' ');
      out.append(readBelowThousand(groups[i], lower));
      if (!GROUP_UNITS[i].isEmpty()) out.append(' ').append(GROUP_UNITS[i]);
    }
    return out.toString();
  }

  /**
   * @param spellEmptyHundreds true when an empty hundreds place is read "không trăm", which is the
   *     case for every group but the leading one
   */
  private static String readBelowThousand(int value, boolean spellEmptyHundreds) {
    int hundreds = value / 100;
    int tens = value / 10 % 10;
    int units = value % 10;
    StringBuilder out = new StringBuilder();
    if (hundreds > 0) out.append(DIGITS[hundreds]).append(" trăm");
    else if (spellEmptyHundreds) out.append("không trăm");
    if (tens > 1) {
      append(out, DIGITS[tens] + " mươi");
    } else if (tens == 1) {
      append(out, "mười");
    } else if (units > 0 && (hundreds > 0 || spellEmptyHundreds)) {
      append(out, "linh");
    }
    if (units > 0) {
      String word =
          switch (units) {
            case 1 -> tens > 1 ? "mốt" : "một";
            case 5 -> tens > 0 ? "lăm" : "năm";
            default -> DIGITS[units];
          };
      append(out, word);
    }
    return out.toString();
  }

  private static void append(StringBuilder out, String word) {
    if (!out.isEmpty()) out.append(' ');
    out.append(word);
  }
}
