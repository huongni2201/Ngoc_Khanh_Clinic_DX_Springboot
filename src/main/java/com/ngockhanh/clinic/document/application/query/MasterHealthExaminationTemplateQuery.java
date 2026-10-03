package com.ngockhanh.clinic.document.application.query;

import java.util.Optional;
import java.util.UUID;

public interface MasterHealthExaminationTemplateQuery {
  Optional<UUID> findEffectiveVersion();
}
