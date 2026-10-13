package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gives a background print job the values of the blank periodic examination form of many
 * Participants of one batch. The job context carries the organization and the batch; the personal
 * data are read here, when the job runs, never stored in the job.
 */
@Component
@RequiredArgsConstructor
public class ExaminationFormValuesSource implements DocumentValuesSource {
  /** Context key of the organization identifier. */
  public static final String ORGANIZATION_ID = "organizationId";

  /** Context key of the batch identifier. */
  public static final String BATCH_ID = "batchId";

  private final ExaminationFormSourceLoader loader;

  @Override
  public String documentType() {
    return PeriodicFormBlankContract.DOCUMENT_TYPE;
  }

  /**
   * @throws com.ngockhanh.clinic.shared.exception.ResourceNotFoundException when the batch or
   *     organization no longer exists
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the batch no longer allows
   *     printing
   */
  @Override
  public Loaded load(Map<String, String> context, List<UUID> ids) {
    var forms =
        loader.loadMany(
            UUID.fromString(context.get(ORGANIZATION_ID)),
            UUID.fromString(context.get(BATCH_ID)),
            ids);
    return new Loaded(
        forms.forms().stream()
            .map(form -> new Item(form.participantId(), PeriodicFormBlankModel.from(form.source()).toValues()))
            .toList(),
        forms.left().stream().map(left -> new Skipped(left.participantId(), left.reason())).toList());
  }
}
