package com.ngockhanh.clinic.catalog.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogCodeTest {

  @Test
  void trimsAndUppercasesRawInput() {
    CatalogCode code = new CatalogCode("  dept_01.a-b  ");
    assertThat(code.value()).isEqualTo("DEPT_01.A-B");
    assertThat(code.toString()).isEqualTo("DEPT_01.A-B");
  }

  @Test
  void staticFactoryProducesIdenticalResult() {
    CatalogCode c1 = new CatalogCode("room-101");
    CatalogCode c2 = CatalogCode.of("room-101");
    assertThat(c1).isEqualTo(c2);
    assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "1", "DEPT-01", "ROOM_102.B", "MED-PARA-500MG"})
  void allowsValidCanonicalCodes(String raw) {
    CatalogCode code = new CatalogCode(raw);
    assertThat(code.value()).isEqualTo(raw);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t\n"})
  void rejectsBlankOrEmptyCodes(String raw) {
    assertThatThrownBy(() -> new CatalogCode(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsNullCode() {
    assertThatThrownBy(() -> new CatalogCode(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void rejectsCodeExceeding50Characters() {
    String longCode = "A".repeat(51);
    assertThatThrownBy(() -> new CatalogCode(longCode))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("50 characters");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-ABC", "_ROOM", ".SERVICE", "@INV", "CODE WITH SPACE"})
  void rejectsCodesViolatingPattern(String raw) {
    assertThatThrownBy(() -> new CatalogCode(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("letters, digits");
  }
}
