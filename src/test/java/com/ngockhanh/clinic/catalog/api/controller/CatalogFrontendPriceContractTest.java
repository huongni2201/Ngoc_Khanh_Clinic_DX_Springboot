package com.ngockhanh.clinic.catalog.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.catalog.api.request.CreateServiceRequest;
import com.ngockhanh.clinic.catalog.api.request.UpdateServiceRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Checks the decimal-string payload emitted by the existing frontend service form. */
class CatalogFrontendPriceContractTest {
  @Test
  void acceptsFrontendDecimalStringsForCreateAndUpdate() {
    var mapper = JsonMapper.builder().build();
    var create =
        mapper.readValue(
            """
            {"code":"KHAM","name":"Synthetic service","serviceType":"CONSULTATION",
             "performingDepartmentId":"11111111-1111-4111-8111-111111111113",
             "specialtyId":null,"unitPrice":"150000.50","preparationInstruction":null}
            """,
            CreateServiceRequest.class);
    var update =
        mapper.readValue(
            """
            {"name":"Synthetic service","serviceType":"CONSULTATION",
             "specialtyId":null,"unitPrice":"150000.50",
             "preparationInstruction":null,"rowVersion":0}
            """,
            UpdateServiceRequest.class);
    assertThat(create.unitPrice()).isEqualByComparingTo(new BigDecimal("150000.50"));
    assertThat(update.unitPrice()).isEqualByComparingTo(new BigDecimal("150000.50"));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(create)).isEmpty();
      assertThat(factory.getValidator().validate(update)).isEmpty();
    }
  }
}
