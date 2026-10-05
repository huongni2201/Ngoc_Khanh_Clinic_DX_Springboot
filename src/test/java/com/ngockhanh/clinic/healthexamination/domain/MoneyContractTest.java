package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

class MoneyContractTest {
  @Test
  void acceptsTheLargestCleanSlateAmountAndZero() {
    assertThat(Money.vnd("999999999999.99").amount()).isEqualByComparingTo("999999999999.99");
    assertThat(Money.vnd("0.00").amount()).isZero();
  }

  @Test
  void rejectsOverflowExcessScaleAndNegativeAmountsAtTheDomainBoundary() {
    for (String amount : new String[] {"1000000000000.00", "1E12", "0.001", "-0.01"}) {
      assertThatThrownBy(() -> Money.vnd(amount)).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
