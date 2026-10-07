package com.ngockhanh.clinic.healthexamination.infrastructure.word;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VietnameseAmountInWordsTest {
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|Không đồng chẵn",
        "1|Một đồng chẵn",
        "10|Mười đồng chẵn",
        "11|Mười một đồng chẵn",
        "15|Mười lăm đồng chẵn",
        "21|Hai mươi mốt đồng chẵn",
        "25|Hai mươi lăm đồng chẵn",
        "40|Bốn mươi đồng chẵn",
        "100|Một trăm đồng chẵn",
        "105|Một trăm linh năm đồng chẵn",
        "110|Một trăm mười đồng chẵn",
        "115|Một trăm mười lăm đồng chẵn",
        "999|Chín trăm chín mươi chín đồng chẵn",
        "1000|Một nghìn đồng chẵn",
        "1001|Một nghìn không trăm linh một đồng chẵn",
        "21000|Hai mươi mốt nghìn đồng chẵn",
        "150000|Một trăm năm mươi nghìn đồng chẵn",
        "1000000|Một triệu đồng chẵn",
        "1005000|Một triệu không trăm linh năm nghìn đồng chẵn",
        "1500000|Một triệu năm trăm nghìn đồng chẵn",
        "1000001|Một triệu không trăm linh một đồng chẵn",
        "12345678|Mười hai triệu ba trăm bốn mươi lăm nghìn sáu trăm bảy mươi tám đồng chẵn",
        "1000000000|Một tỷ đồng chẵn",
        "1000500000|Một tỷ năm trăm nghìn đồng chẵn",
        "2000000000000|Hai nghìn tỷ đồng chẵn",
      })
  void readsWholeAmounts(String amount, String expected) {
    assertThat(VietnameseAmountInWords.of(new BigDecimal(amount))).isEqualTo(expected);
  }

  @Test
  void readsScaleTwoAmountsAsWholeAmounts() {
    assertThat(VietnameseAmountInWords.of(new BigDecimal("1005000.00")))
        .isEqualTo("Một triệu không trăm linh năm nghìn đồng chẵn");
  }

  @Test
  void readsHundredthsAsXu() {
    assertThat(VietnameseAmountInWords.of(new BigDecimal("1500.50")))
        .isEqualTo("Một nghìn năm trăm đồng và năm mươi xu");
  }

  @Test
  void roundsBeyondTwoDecimals() {
    assertThat(VietnameseAmountInWords.of(new BigDecimal("99.999")))
        .isEqualTo("Một trăm đồng chẵn");
  }

  @Test
  void rejectsNullAndNegativeAmounts() {
    assertThatThrownBy(() -> VietnameseAmountInWords.of(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> VietnameseAmountInWords.of(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
