package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.document.application.contract.DocumentTypeContract;
import com.ngockhanh.clinic.document.application.contract.PlaceholderSpec;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.NewPublishedVersion;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds version 1 of each document type from the starter {@code .docx} shipped with the code. A
 * type is seeded only when it has no version at all; afterwards the templates belong to the clinic
 * and a newer starter in the code never replaces them. The starter must use only placeholders of its
 * contract and must contain every required one, so a mismatch fails at startup, not at print time.
 */
@Service
public class StarterTemplateSeeder {
  static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final Logger log = LoggerFactory.getLogger(StarterTemplateSeeder.class);

  private final List<DocumentTypeContract> contracts;
  private final TemplateCatalog catalog;
  private final DocumentStorage storage;
  private final DocxTemplateEngine engine;
  private final Clock clock;

  public StarterTemplateSeeder(
      List<DocumentTypeContract> contracts,
      TemplateCatalog catalog,
      DocumentStorage storage,
      DocxTemplateEngine engine,
      Clock clock) {
    this.contracts = contracts;
    this.catalog = catalog;
    this.storage = storage;
    this.engine = engine;
    this.clock = clock;
  }

  /**
   * Seeds every document type that has no version yet.
   *
   * @return the number of document types seeded
   * @throws IllegalStateException when a starter does not match its contract
   */
  @Transactional
  public int seed() {
    int seeded = 0;
    for (DocumentTypeContract contract : contracts) if (seedOne(contract)) seeded++;
    return seeded;
  }

  private boolean seedOne(DocumentTypeContract contract) {
    String type = contract.documentType();
    catalog.lockForSeeding(type);
    if (catalog.hasVersions(type)) return false;

    byte[] docx = storage.read("CLASSPATH", contract.starterResource(), null);
    requireMatchesContract(contract, engine.placeholders(docx));
    var paper = PaperSizes.classify(engine.pageLayout(docx));

    UUID templateId =
        catalog
            .findDefaultTemplateId(type)
            .orElseGet(
                () -> {
                  UUID id = UuidV7Generator.generate();
                  catalog.insertDefaultTemplate(
                      id, contract.templateCode(), contract.templateName(), type);
                  return id;
                });
    UUID fileId = UuidV7Generator.generate();
    String resource = contract.starterResource();
    catalog.insertFile(
        new NewFile(
            fileId,
            "CLASSPATH",
            resource,
            resource.substring(resource.lastIndexOf('/') + 1),
            DOCX_CONTENT_TYPE,
            docx.length,
            sha256(docx)));
    catalog.insertPublishedVersion(
        new NewPublishedVersion(
            UuidV7Generator.generate(),
            templateId,
            1,
            fileId,
            contract.contractVersion(),
            contract.renderMode(),
            paper.paperSize(),
            paper.orientation(),
            paper.widthMm(),
            paper.heightMm(),
            contract.pagesPerRecord() == null ? null : contract.pagesPerRecord().shortValue(),
            clock.instant()));
    log.info(
        "Starter template seeded: documentType={}, paperSize={}, orientation={}",
        type,
        paper.paperSize(),
        paper.orientation());
    return true;
  }

  private static void requireMatchesContract(DocumentTypeContract contract, Set<String> found) {
    Set<String> unknown = new TreeSet<>(found);
    unknown.removeAll(contract.placeholderKeys());
    Set<String> missing = new TreeSet<>();
    for (PlaceholderSpec spec : contract.placeholders())
      if (spec.required() && !found.contains(spec.key())) missing.add(spec.key());
    if (!unknown.isEmpty() || !missing.isEmpty())
      throw new IllegalStateException(
          "Starter template of "
              + contract.documentType()
              + " does not match its contract; unknown="
              + unknown
              + ", missing required="
              + missing);
  }

  private static byte[] sha256(byte[] content) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(content);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
