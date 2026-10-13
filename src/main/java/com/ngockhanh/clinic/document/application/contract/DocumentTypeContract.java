package com.ngockhanh.clinic.document.application.contract;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes one document type: which placeholders its templates can use and which Word file starts
 * it. The owning business module implements it as a Spring bean. Evolution rule: adding an optional
 * placeholder keeps {@link #contractVersion()}; renaming, removing, retyping or adding a required
 * placeholder raises it.
 */
public interface DocumentTypeContract {
  /** Stable code of the document type, stored as {@code templates.template_type}. */
  String documentType();

  /** Code of the default template of this type, stored as {@code templates.code}. */
  String templateCode();

  /** Vietnamese display name of the default template. */
  String templateName();

  /** Version of this contract; copied to every template version made against it. */
  int contractVersion();

  /** Classpath location of the starter {@code .docx} that seeds version 1. */
  String starterResource();

  /** How the template is rendered: {@code MASTER_FORM}, {@code ONE_PER_SERVICE} or {@code MERGE_BY_TEMPLATE}. */
  default String renderMode() {
    return "MASTER_FORM";
  }

  /** Pages one record occupies in the starter, or {@code null} when it varies. */
  Integer pagesPerRecord();

  /** Every placeholder a template of this type may use. */
  List<PlaceholderSpec> placeholders();

  /** Keys of {@link #placeholders()}. */
  default Set<String> placeholderKeys() {
    return placeholders().stream().map(PlaceholderSpec::key).collect(Collectors.toUnmodifiableSet());
  }
}
