package com.ngockhanh.clinic.healthexamination.application.validation;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class ParticipantDayAllocator {
  public List<HealthExaminationBatchDay> selectedDays(
      List<HealthExaminationBatchDay> available, List<UUID> selected) {
    if (selected == null
        || selected.isEmpty()
        || selected.stream().distinct().count() != selected.size())
      throw new IllegalArgumentException("Select distinct examination days for this import");
    var days = available.stream().filter(day -> selected.contains(day.id())).toList();
    if (days.size() != selected.size())
      throw new BusinessRuleException("Selected examination day is outside this batch") {};
    return days;
  }

  public void assignDays(
      List<HealthExaminationImportRow> rows,
      List<HealthExaminationBatchDay> days,
      Map<UUID, Long> existingCounts) {
    var counts = new HashMap<>(existingCounts);
    var order =
        Comparator.comparingLong(
                (HealthExaminationBatchDay day) -> counts.getOrDefault(day.id(), 0L))
            .thenComparing(HealthExaminationBatchDay::examinationDate)
            .thenComparing(day -> day.id().toString());
    for (var row :
        rows.stream()
            .sorted(Comparator.comparingInt(HealthExaminationImportRow::getRowNumber))
            .toList()) {
      var day = days.stream().min(order).orElseThrow();
      row.assignDay(AggregateId.of(day.id()));
      counts.merge(day.id(), 1L, Long::sum);
    }
  }
}
