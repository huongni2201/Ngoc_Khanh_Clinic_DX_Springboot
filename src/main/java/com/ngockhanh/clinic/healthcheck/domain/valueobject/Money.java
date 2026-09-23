package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.signum() < 0 || amount.scale() > 2 || !"VND".equals(currency)) {
            throw new IllegalArgumentException("Invalid corporate price");
        }
    }

    public static Money vnd(String amount) {
        return new Money(new BigDecimal(amount), "VND");
    }
}
