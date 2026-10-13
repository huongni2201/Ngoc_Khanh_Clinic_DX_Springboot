package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.document.application.port.PageLayout;
import org.junit.jupiter.api.Test;

class PaperSizesTest {
  @Test
  void namesStandardPapersInEitherOrientation() {
    var a3 = PaperSizes.classify(new PageLayout(420.05, 297.03));
    assertThat(a3.paperSize()).isEqualTo("A3");
    assertThat(a3.orientation()).isEqualTo("LANDSCAPE");
    assertThat(a3.widthMm()).isEqualByComparingTo("420.1");
    assertThat(a3.heightMm()).isEqualByComparingTo("297.0");

    assertThat(PaperSizes.classify(new PageLayout(210, 297)).paperSize()).isEqualTo("A4");
    assertThat(PaperSizes.classify(new PageLayout(210, 297)).orientation()).isEqualTo("PORTRAIT");
    assertThat(PaperSizes.classify(new PageLayout(210, 148)).paperSize()).isEqualTo("A5");
    assertThat(PaperSizes.classify(new PageLayout(105, 148)).paperSize()).isEqualTo("A6");
  }

  @Test
  void anythingElseIsCustom() {
    assertThat(PaperSizes.classify(new PageLayout(80, 200)).paperSize()).isEqualTo("CUSTOM");
    assertThat(PaperSizes.classify(new PageLayout(216, 279)).paperSize()).isEqualTo("CUSTOM");
  }
}
