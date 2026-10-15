package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Username prefix generated from a staff member's full name (docs/api/staff-members.md): the last
 * word followed by the first letter of each earlier word, without Vietnamese diacritics. {@code
 * Nguyễn Văn An} gives {@code annv}; the repository appends the next free number ({@code annv1}).
 */
public record UsernameBase(String value) {
  /** Leaves room for a nine-digit number within {@code accounts.username varchar(150)}. */
  public static final Integer MAX_LENGTH = 140;

  public UsernameBase {
    if (value == null || !value.matches("[a-z]{1," + MAX_LENGTH + "}"))
      throw new IllegalArgumentException("Username base must be 1 to 140 letters a-z");
  }

  /**
   * Builds the prefix from a full name.
   *
   * @param fullName staff member's full name
   * @return the prefix, at most 140 letters
   * @throws IllegalArgumentException when the name has no letter a-z after removing diacritics
   */
  public static UsernameBase from(String fullName) {
    if (fullName == null) throw new IllegalArgumentException("Full name is required");
    String plain =
        Normalizer.normalize(fullName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'd')
            .toLowerCase(Locale.ROOT);
    List<String> words = new ArrayList<>();
    for (String word : plain.trim().split("\\s+")) {
      String letters = word.replaceAll("[^a-z]", "");
      if (!letters.isEmpty()) words.add(letters);
    }
    if (words.isEmpty()) throw new IllegalArgumentException("Full name has no usable letter");

    StringBuilder base = new StringBuilder(words.getLast());
    for (String word : words.subList(0, words.size() - 1)) base.append(word.charAt(0));
    String value = base.length() > MAX_LENGTH ? base.substring(0, MAX_LENGTH) : base.toString();
    return new UsernameBase(value);
  }
}
