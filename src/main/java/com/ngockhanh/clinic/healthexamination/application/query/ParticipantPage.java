package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.List;

/** One page of Participant rows with the number of rows matching the same filters. */
public record ParticipantPage(List<ParticipantSummary> items, long totalElements) {
  public ParticipantPage {
    items = List.copyOf(items);
  }
}
