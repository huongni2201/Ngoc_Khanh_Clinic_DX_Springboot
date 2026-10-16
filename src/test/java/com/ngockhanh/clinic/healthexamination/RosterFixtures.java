package com.ngockhanh.clinic.healthexamination;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import java.time.*;
import java.util.*;

public final class RosterFixtures {
  public static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
  public static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

  public static AggregateId id(long n) {
    return AggregateId.of(new UUID(0, n));
  }

  public static List<
          com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService>
      scope(AggregateId serviceId) {
    return List.of(
        new com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationAgreementService(
            serviceId, id(99), id(1), Money.vnd("200"), Money.vnd("100"), 1, true, 0));
  }
}
