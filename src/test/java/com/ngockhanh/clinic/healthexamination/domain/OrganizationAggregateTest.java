package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationAggregateTest {
  @Test
  void updateDetailsChangesTheAggregateAndPreservesIdentityAndVersion() {
    AggregateId id = AggregateId.of(UUID.randomUUID());
    Organization organization = organization(id, "ORG-1", "Clinic One");

    organization.updateDetails(
        "Clinic Two",
        "TAX-2",
        "0902",
        "office2@example.test",
        "Second Address",
        "Contact Two",
        "0903",
        "contact2@example.test");

    assertThat(organization.id()).isEqualTo(id);
    assertThat(organization.name()).isEqualTo("Clinic Two");
    assertThat(organization.taxCode()).isEqualTo("TAX-2");
    assertThat(organization.status()).isEqualTo("ACTIVE");
    assertThat(organization.rowVersion()).isZero();
  }

  @Test
  void equalityUsesAggregateIdentity() {
    AggregateId id = AggregateId.of(UUID.randomUUID());

    assertThat(organization(id, "TAX-1", "Clinic One"))
        .isEqualTo(organization(id, "TAX-2", "Clinic Two"));
  }

  private static Organization organization(AggregateId id, String taxCode, String name) {
    return Organization.create(
        id,
        name,
        taxCode,
        "0901",
        "office@example.test",
        "Address",
        "Contact",
        "0902",
        "contact@example.test");
  }
}
