package com.ngockhanh.clinic.catalog.domain.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogServiceMoneyValidationTest {
  CatalogService create(String amount) {
    return CatalogService.create(
        UUID.randomUUID(),
        new CatalogCode("MONEY"),
        "Synthetic",
        ServiceType.CONSULTATION,
        UUID.randomUUID(),
        null,
        new BigDecimal(amount),
        null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000000000000.00", "1E+12", "-1", "0.001"})
  void rejectsAmountsOutsideDatabaseContract(String amount) {
    assertThatThrownBy(() -> create(amount)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "150000", "999999999999.99", "1E+11"})
  void acceptsAmountsWithinNumericFourteenTwo(String amount) {
    assertThat(create(amount).unitPrice()).isEqualByComparingTo(amount);
  }
}
