package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "agreementCode", "agreementName", "validFrom", "status", "createdAt"})
public class HealthExaminationAgreementListRequest extends BasePagination {}
