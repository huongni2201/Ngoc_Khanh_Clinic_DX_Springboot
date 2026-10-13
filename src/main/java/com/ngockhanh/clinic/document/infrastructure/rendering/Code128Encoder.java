package com.ngockhanh.clinic.document.infrastructure.rendering;

import java.util.ArrayList;
import java.util.List;

/**
 * Code 128 (ISO/IEC 15417) encoder for printable ASCII. It uses code set B and switches to code set
 * C for runs of digits whenever that makes the symbol shorter, choosing the shortest symbol by
 * dynamic programming. Code set A (control characters) is not supported.
 */
final class Code128Encoder {
  /** Bar and space widths, bar first, of symbol values 0 to 105 and of the stop pattern (106). */
  private static final String[] PATTERNS = {
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212",
    "221213", "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221",
    "223211", "221132", "221231", "213212", "223112", "312131", "311222", "321122", "321221",
    "312212", "322112", "322211", "212123", "212321", "232121", "111323", "131123", "131321",
    "112313", "132113", "132311", "211313", "231113", "231311", "112133", "112331", "132131",
    "113123", "113321", "133121", "313121", "211331", "231131", "213113", "213311", "213131",
    "311123", "311321", "331121", "312113", "312311", "332111", "314111", "221411", "431111",
    "111224", "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
    "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111", "111242",
    "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311",
    "113141", "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
  };

  private static final int START_B = 104;
  private static final int START_C = 105;
  private static final int CODE_C = 99;
  private static final int CODE_B = 100;
  private static final int STOP = 106;
  private static final int SET_B = 0;
  private static final int SET_C = 1;

  private Code128Encoder() {}

  /**
   * Returns the widths, in modules, of the alternating bars and spaces of the whole symbol, starting
   * with a bar and ending with the stop bar. The quiet zones are not included.
   *
   * @throws IllegalArgumentException when the text is empty or holds a character outside space to
   *     tilde
   */
  static int[] widths(String text) {
    List<Integer> symbols = symbols(text);
    int[] widths = new int[symbols.size() * 6 + 7];
    int at = 0;
    for (int symbol : symbols) at = append(widths, at, PATTERNS[symbol]);
    append(widths, at, PATTERNS[STOP]);
    return widths;
  }

  /** The symbol values, from the start symbol to the check symbol. */
  static List<Integer> symbols(String text) {
    if (text == null || text.isEmpty()) throw new IllegalArgumentException("Nothing to encode");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < ' ' || c > '~')
        throw new IllegalArgumentException("Code 128 here encodes printable ASCII only");
    }
    List<Integer> symbols = shortest(text);
    long sum = symbols.get(0);
    for (int i = 1; i < symbols.size(); i++) sum += (long) symbols.get(i) * i;
    symbols.add((int) (sum % 103));
    return symbols;
  }

  /** Total width of the symbol in modules (without the quiet zones). */
  static int modules(String text) {
    int total = 0;
    for (int width : widths(text)) total += width;
    return total;
  }

  private static int append(int[] widths, int at, String pattern) {
    for (int i = 0; i < pattern.length(); i++) widths[at++] = pattern.charAt(i) - '0';
    return at;
  }

  private static boolean digits(String text, int at) {
    return at + 1 < text.length()
        && Character.isDigit(text.charAt(at))
        && Character.isDigit(text.charAt(at + 1))
        && text.charAt(at) < 128
        && text.charAt(at + 1) < 128;
  }

  /** The start symbol and the data symbols of the shortest encoding. */
  private static List<Integer> shortest(String text) {
    int n = text.length();
    int infinity = Integer.MAX_VALUE / 2;
    // cost[i][s]: fewest symbols after the first i characters, the last one being in set s.
    int[][] cost = new int[n + 1][2];
    int[][] fromIndex = new int[n + 1][2];
    int[][] fromSet = new int[n + 1][2];
    int[][] viaSwitch = new int[n + 1][2];
    for (int[] row : cost) java.util.Arrays.fill(row, infinity);
    cost[0][SET_B] = 1;
    cost[0][SET_C] = 1;
    for (int i = 0; i < n; i++) {
      for (int set = SET_B; set <= SET_C; set++) {
        int here = cost[i][set];
        if (here >= infinity) continue;
        // One character in set B, after switching to B when the run was in set C.
        int toB = here + 1 + (set == SET_C ? 1 : 0);
        if (toB < cost[i + 1][SET_B]) {
          cost[i + 1][SET_B] = toB;
          fromIndex[i + 1][SET_B] = i;
          fromSet[i + 1][SET_B] = set;
          viaSwitch[i + 1][SET_B] = set == SET_C ? 1 : 0;
        }
        // Two digits in set C, after switching to C when the run was in set B.
        if (digits(text, i)) {
          int toC = here + 1 + (set == SET_B ? 1 : 0);
          if (toC < cost[i + 2][SET_C]) {
            cost[i + 2][SET_C] = toC;
            fromIndex[i + 2][SET_C] = i;
            fromSet[i + 2][SET_C] = set;
            viaSwitch[i + 2][SET_C] = set == SET_B ? 1 : 0;
          }
        }
      }
    }
    int set = cost[n][SET_B] <= cost[n][SET_C] ? SET_B : SET_C;
    // Walk back, collecting data (and switch) symbols in reverse.
    List<Integer> reversed = new ArrayList<>();
    int index = n;
    while (index > 0) {
      int from = fromIndex[index][set];
      int previousSet = fromSet[index][set];
      if (set == SET_B) reversed.add(text.charAt(from) - ' ');
      else reversed.add((text.charAt(from) - '0') * 10 + (text.charAt(from + 1) - '0'));
      if (viaSwitch[index][set] == 1) reversed.add(set == SET_B ? CODE_B : CODE_C);
      index = from;
      set = previousSet;
    }
    // After the walk, "set" is the set of the start symbol: B or C.
    List<Integer> symbols = new ArrayList<>(reversed.size() + 2);
    symbols.add(set == SET_B ? START_B : START_C);
    for (int i = reversed.size() - 1; i >= 0; i--) symbols.add(reversed.get(i));
    return symbols;
  }
}
