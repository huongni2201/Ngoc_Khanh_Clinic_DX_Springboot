package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.List;

/** One page of examination detail rows with the number of rows matching the same filters. */
public record ExaminationDetailPage(List<ExaminationDetailRow> items, long totalElements) {
  public ExaminationDetailPage {
    items = List.copyOf(items);
  }
}
