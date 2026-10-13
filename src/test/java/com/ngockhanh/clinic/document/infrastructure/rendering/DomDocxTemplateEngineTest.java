package com.ngockhanh.clinic.document.infrastructure.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.document.application.port.ImageSlot;
import com.ngockhanh.clinic.document.application.port.PageLayout;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DomDocxTemplateEngineTest {
  private static final String A4_PORTRAIT =
      "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>";
  private static final String STARTER =
      "document-templates/health-exam-periodic-form-blank/starter-v1.docx";

  private final DomDocxTemplateEngine engine = new DomDocxTemplateEngine();

  @Test
  void findsPlaceholdersSplitAcrossRunsAndInsideTextBoxes() throws Exception {
    byte[] docx = docx(SPLIT_RUNS + A4_PORTRAIT);

    assertThat(engine.placeholders(docx))
        .containsExactlyInAnyOrder(
            "participant.fullName", "batch.code", "organization.name", "participant.code");
  }

  @Test
  void writesTheWholeValueIntoTheRunThatHoldsTheOpeningBraces() throws Exception {
    byte[] filled =
        engine.fill(
            docx(SPLIT_RUNS + A4_PORTRAIT),
            Map.of(
                "participant.fullName", "A & <B>",
                "batch.code", "K1",
                "organization.name", "Org",
                "participant.code", "C1"));

    String xml = part(filled, "word/document.xml");
    assertThat(xml)
        .contains("<w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">Họ: A &amp; &lt;B&gt;</w:t>")
        .contains("<w:t xml:space=\"preserve\"> - </w:t>")
        .contains("K1!")
        .contains("Box Org")
        .contains("tail C1")
        .doesNotContain("{{");
  }

  @Test
  void aKeyWithoutAValueBecomesEmptyText() throws Exception {
    String xml = part(engine.fill(docx(SPLIT_RUNS + A4_PORTRAIT), Map.of()), "word/document.xml");

    assertThat(xml).doesNotContain("{{").contains("Họ: ").contains("tail ");
  }

  @Test
  void valuesAreTextOnly() throws Exception {
    byte[] docx =
        docx("<w:p><w:r><w:t>{{participant.code}}</w:t></w:r></w:p>" + A4_PORTRAIT);

    String xml =
        part(
            engine.fill(docx, Map.of("participant.code", "a\nb\u0000c\uD800d")),
            "word/document.xml");

    assertThat(xml).contains(">a b c d<");
  }

  @Test
  void anAbsurdlyLongValueIsCutWithoutSplittingASurrogatePair() throws Exception {
    byte[] docx =
        docx("<w:p><w:r><w:t>{{participant.code}}</w:t></w:r></w:p>" + A4_PORTRAIT);
    // The pair 😀 starts at index 999 and would be cut in half at 1000 characters.
    String value = "a".repeat(DomDocxTemplateEngine.MAX_VALUE_CHARS - 1) + "\uD83D\uDE00tail";

    String xml = part(engine.fill(docx, Map.of("participant.code", value)), "word/document.xml");

    assertThat(xml)
        .contains(">" + "a".repeat(DomDocxTemplateEngine.MAX_VALUE_CHARS - 1) + "<")
        .doesNotContain("tail");
  }

  @Test
  void readsAndComparesPageSizesOfAllSections() throws Exception {
    PageLayout a4 = engine.pageLayout(docx(A4_PORTRAIT));
    assertThat(a4.widthMm()).isBetween(209.9, 210.1);
    assertThat(a4.heightMm()).isBetween(296.9, 297.1);
    assertThat(a4.landscape()).isFalse();

    String landscape =
        "<w:p><w:pPr><w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\"/></w:sectPr></w:pPr></w:p>";
    assertThatThrownBy(() -> engine.pageLayout(docx(landscape + A4_PORTRAIT)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mixes");
    assertThatThrownBy(() -> engine.pageLayout(docx("<w:p/>")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ignoresThePageSizeKeptInATrackedSectionChange() throws Exception {
    String tracked =
        "<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\"/><w:sectPrChange w:id=\"1\"><w:sectPr>"
            + "<w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr></w:sectPrChange></w:sectPr>";

    assertThat(engine.pageLayout(docx(tracked)).landscape()).isTrue();
  }

  @Test
  void rejectsFilesThatAreNotWordDocumentsOrCarryADoctype() throws Exception {
    assertThatThrownBy(() -> engine.placeholders("not a zip".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class);

    String xxe =
        "<!DOCTYPE x [<!ENTITY e \"boom\">]>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">&e;</w:document>";
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write(xxe.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    assertThatThrownBy(() -> engine.placeholders(out.toByteArray()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fillsTheStarterTemplateAndCopiesEveryOtherPartUnchanged() throws Exception {
    byte[] starter = starter();
    Map<String, String> values = new LinkedHashMap<>();
    for (String key : engine.placeholders(starter)) values.put(key, "«" + key + "»");

    byte[] filled = engine.fill(starter, values);

    Map<String, byte[]> before = parts(starter);
    Map<String, byte[]> after = parts(filled);
    assertThat(after.keySet()).isEqualTo(before.keySet());
    String xml = new String(after.get("word/document.xml"), StandardCharsets.UTF_8);
    assertThat(xml).doesNotContain("{{").contains("«participant.fullNameUpper»");
    for (var part : before.entrySet())
      if (!part.getKey().equals("word/document.xml"))
        assertThat(after.get(part.getKey())).as(part.getKey()).isEqualTo(part.getValue());
    assertThat(engine.placeholders(filled)).isEmpty();
  }

  @Test
  void theStarterIsAnA3LandscapeBookletWithItsFourteenPlaceholders() throws Exception {
    byte[] starter = starter();

    assertThat(new TreeSet<>(engine.placeholders(starter)))
        .containsExactly(
            "participant.address",
            "participant.age",
            "participant.barcode",
            "participant.code",
            "participant.dateOfBirth",
            "participant.ethnicity",
            "participant.fullNameUpper",
            "participant.identificationIssueDate",
            "participant.identificationIssuePlace",
            "participant.identificationNumber",
            "participant.phone",
            "participant.positionName",
            "participant.sex",
            "participant.workplace");
    PageLayout layout = engine.pageLayout(starter);
    assertThat(layout.landscape()).isTrue();
    assertThat(layout.widthMm()).isBetween(419.5, 420.5);
    assertThat(layout.heightMm()).isBetween(296.5, 297.5);
  }

  @Test
  void aPicturePlaceholderIsReadFromTheAlternativeTextWithItsFrameSize() throws Exception {
    byte[] docx = docx(picture("{{@participant.barcode}}", 1760400, 324000) + A4_PORTRAIT, true);

    assertThat(engine.imageSlots(docx))
        .containsExactly(new ImageSlot("participant.barcode", 48.9, 9.0));
    assertThat(engine.placeholders(docx)).containsExactly("participant.barcode");
    assertThat(engine.imageSlots(docx(SPLIT_RUNS + A4_PORTRAIT))).isEmpty();
    assertThat(engine.imageSlots(docx(picture("A logo", 1000, 1000) + A4_PORTRAIT, true)))
        .as("a picture with ordinary alternative text is not a placeholder")
        .isEmpty();
  }

  @Test
  void aPictureIsReplacedByANewImagePartAtTheSameSizeAndTheOriginalIsLeftAlone() throws Exception {
    byte[] docx = docx(picture(" {{@ participant.barcode }} ", 1760400, 324000) + A4_PORTRAIT, true);
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    var asked = new java.util.ArrayList<String>();

    byte[] filled =
        engine.fill(
            docx,
            Map.of("participant.barcode", "KSK-2026-001-0001"),
            (key, value, slot) -> {
              asked.add(key + "=" + value + "@" + slot.widthMm() + "x" + slot.heightMm());
              return png;
            });

    assertThat(asked).containsExactly("participant.barcode=KSK-2026-001-0001@48.9x9.0");
    Map<String, byte[]> after = parts(filled);
    assertThat(after.get("word/media/nkc-image-1.png")).isEqualTo(png);
    assertThat(after.get("word/media/placeholder.png")).isEqualTo(PLACEHOLDER_PNG);
    String rels = part(filled, "word/_rels/document.xml.rels");
    assertThat(rels)
        .contains("Target=\"media/nkc-image-1.png\"")
        .contains("Target=\"media/placeholder.png\"")
        .contains("rId1");
    String xml = part(filled, "word/document.xml");
    assertThat(xml)
        .contains("r:embed=\"rIdNkc1\"")
        .doesNotContain("r:embed=\"rId1\"")
        .contains("cx=\"1760400\"")
        .contains("cy=\"324000\"")
        .doesNotContain("{{@");
    assertThat(rels).contains("Id=\"rIdNkc1\"");
    assertThat(part(filled, "[Content_Types].xml")).contains("Extension=\"png\"");
    assertThat(engine.imageSlots(filled)).isEmpty();
  }

  @Test
  void theMediaTypeOfPngIsDeclaredOnlyOnceAndNeverDuplicated() throws Exception {
    byte[] withPng = docx(picture("{{@participant.barcode}}", 1000, 500) + A4_PORTRAIT, true);
    byte[] filled =
        engine.fill(withPng, Map.of("participant.barcode", "X"), (k, v, s) -> new byte[] {1});
    String types = part(filled, "[Content_Types].xml");

    assertThat(types.split("Extension=\"png\"", -1)).hasSize(2);
    byte[] withoutPng = docx(picture("{{@participant.barcode}}", 1000, 500) + A4_PORTRAIT, false);
    String added =
        part(
            engine.fill(withoutPng, Map.of("participant.barcode", "X"), (k, v, s) -> new byte[] {1}),
            "[Content_Types].xml");
    assertThat(added.split("Extension=\"png\"", -1)).hasSize(2);
    assertThat(added).contains("image/png");
  }

  @Test
  void aPictureWithoutAValueOrWithoutAnImageIsRemovedNeverLeftAsArtwork() throws Exception {
    byte[] docx = docx(picture("{{@participant.barcode}}", 1760400, 324000) + A4_PORTRAIT, true);

    for (byte[] filled :
        List.of(
            engine.fill(docx, Map.of()),
            engine.fill(docx, Map.of("participant.barcode", "  "), (k, v, s) -> new byte[] {1}),
            engine.fill(docx, Map.of("participant.barcode", "X"), (k, v, s) -> null))) {
      String xml = part(filled, "word/document.xml");
      assertThat(xml).doesNotContain("w:drawing").doesNotContain("r:embed");
      assertThat(parts(filled).keySet()).noneMatch(name -> name.contains("nkc-image"));
    }
  }

  @Test
  void aPicturePlaceholderWithoutASizeOrAPictureIsAnInvalidTemplate() throws Exception {
    byte[] noSize = docx(picture("{{@participant.barcode}}", 0, 0) + A4_PORTRAIT, true);
    assertThatThrownBy(() -> engine.imageSlots(noSize))
        .isInstanceOf(IllegalArgumentException.class);

    byte[] noPicture =
        docx(
            picture("{{@participant.barcode}}", 1000, 500).replace("<a:blip r:embed=\"rId1\"/>", "")
                + A4_PORTRAIT,
            true);
    assertThatThrownBy(() -> engine.fill(noPicture, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theStarterGetsAPictureOfExactlyItsFrameSize() throws Exception {
    byte[] starter = starter();
    Code128BarcodeGenerator generator = new Code128BarcodeGenerator();

    byte[] filled =
        engine.fill(
            starter,
            Map.of("participant.barcode", "KSK-2026-001-0001"),
            (key, value, slot) -> generator.code128Png(value, slot.widthMm(), slot.heightMm()));

    var image =
        javax.imageio.ImageIO.read(
            new ByteArrayInputStream(parts(filled).get("word/media/nkc-image-1.png")));
    assertThat(engine.imageSlots(starter)).hasSize(1);
    assertThat(image.getWidth()).isEqualTo(2310);
    assertThat(image.getHeight()).isEqualTo(425);
  }

  private static final byte[] PLACEHOLDER_PNG = {(byte) 0x89, 'P', 'N', 'G', 9, 9, 9};

  /** An inline picture whose alternative text is {@code descr}, linked as rId1. */
  private static String picture(String descr, long cx, long cy) {
    return "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
        + "<wp:docPr id=\"1\" name=\"p\" descr=\"" + descr + "\"/>"
        + "<a:graphic><a:graphicData><pic:pic><pic:nvPicPr><pic:cNvPr id=\"1\" name=\"p.png\" descr=\""
        + descr + "\"/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rId1\"/></pic:blipFill>"
        + "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
  }

  private static final String SPLIT_RUNS =
      "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Họ: {{par</w:t></w:r><w:r><w:t>ticipant.</w:t></w:r>"
          + "<w:proofErr w:type=\"spellStart\"/><w:r><w:t xml:space=\"preserve\">fullName}} - </w:t></w:r>"
          + "<w:r><w:t>{{ batch.code }}!</w:t></w:r></w:p>"
          + "<w:p><w:r><w:pict><v:shape><v:textbox><w:txbxContent><w:p><w:r>"
          + "<w:t>Box {{organization.name}}</w:t></w:r></w:p></w:txbxContent></v:textbox></v:shape>"
          + "</w:pict></w:r><w:r><w:t>tail {{participant.code}}</w:t></w:r></w:p>";

  private static byte[] starter() throws IOException {
    try (var in = DomDocxTemplateEngineTest.class.getClassLoader().getResourceAsStream(STARTER)) {
      assertThat(in).as("starter template on the classpath").isNotNull();
      return in.readAllBytes();
    }
  }

  private static byte[] docx(String body) throws IOException {
    return docx(body, false);
  }

  /** A document whose picture rId1 points at media/placeholder.png; PNG is declared if asked. */
  private static byte[] docx(String body, boolean declarePng) throws IOException {
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zip.write(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                  + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                  + (declarePng ? "<Default Extension=\"png\" ContentType=\"image/png\"/>" : "")
                  + "</Types>")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
      zip.write(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                  + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/placeholder.png\"/>"
                  + "</Relationships>")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("word/media/placeholder.png"));
      zip.write(PLACEHOLDER_PNG);
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write(
          ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                  + " xmlns:v=\"urn:schemas-microsoft-com:vml\""
                  + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
                  + " xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""
                  + " xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""
                  + " xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>"
                  + body
                  + "</w:body></w:document>")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  private static Map<String, byte[]> parts(byte[] docx) throws IOException {
    Map<String, byte[]> parts = new LinkedHashMap<>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
        parts.put(entry.getName(), zip.readAllBytes());
    }
    return parts;
  }

  private static String part(byte[] docx, String name) throws IOException {
    return new String(parts(docx).get(name), StandardCharsets.UTF_8);
  }
}
