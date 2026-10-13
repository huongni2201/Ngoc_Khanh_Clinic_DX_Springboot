package com.ngockhanh.clinic.document.application.contract;

import java.util.regex.Pattern;

/**
 * One placeholder a template can use, written {@code {{key}}} in the Word file.
 *
 * @param key dotted name such as {@code participant.fullName}; letters, digits and dots only
 * @param label Vietnamese label shown to the person who writes the template
 * @param example sample value shown next to the label
 * @param required whether a template must contain this placeholder to be valid
 */
public record PlaceholderSpec(String key, String label, String example, boolean required) {
  private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*(\\.[A-Za-z][A-Za-z0-9]*)*");

  public PlaceholderSpec {
    if (key == null || !KEY.matcher(key).matches())
      throw new IllegalArgumentException("Invalid placeholder key: " + key);
    if (label == null || label.isBlank())
      throw new IllegalArgumentException("Placeholder label is required: " + key);
    example = example == null ? "" : example;
  }
}
