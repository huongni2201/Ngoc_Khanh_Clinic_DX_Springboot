package com.ngockhanh.clinic.document.application.port;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and fills Word templates that hold {@code {{key}}} text placeholders and {@code
 * {{@key}}} picture placeholders.
 */
public interface DocxTemplateEngine {
  /**
   * Supplies the picture that replaces an {@link ImageSlot}.
   *
   * @return PNG bytes, or {@code null} to remove the picture from the document
   */
  @FunctionalInterface
  interface ImageSource {
    byte[] image(String key, String value, ImageSlot slot);
  }

  /**
   * Returns the keys of every placeholder in the document body, headers, footers and text boxes,
   * including the keys of picture placeholders (written without the {@code @}).
   *
   * @throws IllegalArgumentException when the bytes are not a readable {@code .docx}
   */
  Set<String> placeholders(byte[] docx);

  /**
   * Returns the picture frames of the document, in document order, with their sizes.
   *
   * @throws IllegalArgumentException when the bytes are not a readable {@code .docx} or a picture
   *     placeholder has no size or no picture
   */
  List<ImageSlot> imageSlots(byte[] docx);

  /**
   * Reads the page size. All sections must share one size and orientation.
   *
   * @throws IllegalArgumentException when the file is not a readable {@code .docx}, has no page
   *     size, or its sections differ
   */
  PageLayout pageLayout(byte[] docx);

  /**
   * Replaces every text placeholder by its value, keeping the formatting of the first character of
   * the placeholder. A key without a value becomes an empty string. Values are plain text; line
   * breaks and control characters become spaces. Every picture placeholder is removed from the
   * document; use {@link #fill(byte[], Map, ImageSource)} to put a picture in its place.
   *
   * @throws IllegalArgumentException when the bytes are not a readable {@code .docx}
   */
  default byte[] fill(byte[] docx, Map<String, String> values) {
    return fill(docx, values, (key, value, slot) -> null);
  }

  /**
   * Fills text placeholders as {@link #fill(byte[], Map)} does. For each picture placeholder whose
   * key has a non-blank value, the picture returned by {@code images} replaces the placeholder
   * picture at the same size and position; when the value is blank or {@code images} returns {@code
   * null}, the picture is removed so no placeholder artwork is ever printed.
   *
   * @throws IllegalArgumentException when the bytes are not a readable {@code .docx}
   */
  byte[] fill(byte[] docx, Map<String, String> values, ImageSource images);
}
