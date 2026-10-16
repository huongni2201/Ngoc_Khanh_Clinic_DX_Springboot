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
 * Participants of one agreement. The job context carries the organization, the agreement and
 * optionally the session; the personal data are read here, when the job runs, never stored in the
 * job.
 */
@Component
@RequiredArgsConstructor
public class ExaminationFormValuesSource implements DocumentValuesSource {
  /** Context key of the organization identifier. */
  public static final String ORGANIZATION_ID = "organizationId";

  /** Context key of the agreement identifier. */
  public static final String AGREEMENT_ID = "agreementId";

  /** Context key of the optional session whose date the forms show. */
  public static final String SESSION_ID = "sessionId";

  /**
   * Context key jobs queued before agreements existed carry instead of {@link #AGREEMENT_ID}; the
   * identifier is the same agreement, so such a job still renders.
   */
  static final String LEGACY_AGREEMENT_ID = "batchId";

  private final ExaminationFormSourceLoader loader;

  @Override
  public String documentType() {
    return PeriodicFormBlankContract.DOCUMENT_TYPE;
  }

  /**
   * @throws com.ngockhanh.clinic.shared.exception.ResourceNotFoundException when the agreement,
   *     organization or session no longer exists
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the agreement no longer
   *     allows printing
   */
  @Override
  public Loaded load(Map<String, String> context, List<UUID> ids) {
    String agreement =
        context.containsKey(AGREEMENT_ID)
            ? context.get(AGREEMENT_ID)
            : context.get(LEGACY_AGREEMENT_ID);
    String session = context.get(SESSION_ID);
    var forms =
        loader.loadMany(
            UUID.fromString(context.get(ORGANIZATION_ID)),
            UUID.fromString(agreement),
            session == null ? null : UUID.fromString(session),
            ids);
    return new Loaded(
        forms.forms().stream()
            .map(
                form ->
                    new Item(
                        form.participantId(),
                        PeriodicFormBlankModel.from(form.source()).toValues()))
            .toList(),
        forms.left().stream()
            .map(left -> new Skipped(left.participantId(), left.reason()))
            .toList());
  }
}
