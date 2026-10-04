package com.ngockhanh.clinic.healthexamination;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.*;
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
          com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService>
      scope(AggregateId serviceId) {
    return List.of(
        new com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService(
            serviceId, id(99), id(1), Money.vnd("200"), Money.vnd("100"), 1, true, 0));
  }

  public static HealthExaminationImportRow row(int n) {
    return new HealthExaminationImportRow(
        id(100 + n),
        n,
        null,
        "Synthetic Person " + n,
        LocalDate.of(1990, 1, 1),
        "MALE",
        IdentificationNumber.of(String.format("%012d", n)),
        null,
        null,
        "Department",
        "Position",
        List.of());
  }

  public static HealthExaminationBatchReference batch() {
    return new HealthExaminationBatchReference(
        id(1),
        id(2),
        List.of(
            new BatchDay(id(3).value(), LocalDate.of(2026, 10, 4)),
            new BatchDay(id(4).value(), LocalDate.of(2026, 10, 5))),
        BatchStatus.READY,
        0);
  }

  public static HealthExaminationImportJob job(long version) {
    var r = row(1);
    r.assignDay(id(3));
    return new HealthExaminationImportJob(
        id(5),
        id(1),
        id(6),
        NOW,
        List.of(id(3)),
        List.of(r),
        ImportStatus.VALIDATED,
        null,
        null,
        null,
        null,
        version,
        true,
        null);
  }
}
