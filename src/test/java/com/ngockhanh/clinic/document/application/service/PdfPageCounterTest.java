package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfPageCounterTest {
  @Test
  void countsPageObjectsAndIgnoresThePageTreeNodes() {
    String pdf =
        "%PDF-1.7\n1 0 obj\n<</Type/Pages/Count 2/Kids[2 0 R 3 0 R]>>\nendobj\n"
            + "2 0 obj\n<</Type/Page/Parent 1 0 R>>\nendobj\n"
            + "3 0 obj\n<< /Type /Page /Parent 1 0 R >>\nendobj\n%%EOF";

    assertThat(PdfPageCounter.count(bytes(pdf))).hasValue(2);
  }

  @Test
  void givesNoAnswerWhenPagesCannotBeCountedReliably() {
    assertThat(PdfPageCounter.count(null)).isEmpty();
    assertThat(PdfPageCounter.count(bytes("not a pdf at all"))).isEmpty();
    assertThat(PdfPageCounter.count(bytes("%PDF-1.5\n<</Type/ObjStm>>/ObjStm"))).isEmpty();
    assertThat(PdfPageCounter.count(bytes("%PDF-1.7\n<</Type/Pages/Count 0>>"))).isEmpty();
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.ISO_8859_1);
  }
}
