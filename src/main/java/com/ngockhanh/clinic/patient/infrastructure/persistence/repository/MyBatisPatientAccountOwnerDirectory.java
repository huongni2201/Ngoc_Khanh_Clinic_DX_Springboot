package com.ngockhanh.clinic.patient.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.patient.infrastructure.persistence.mapper.PatientAccountOwnerMyBatisMapper;
import com.ngockhanh.clinic.patient.infrastructure.persistence.view.PatientAccountOwnerView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Patient side of {@code accesscontrol::patient-account-owners}: patients as account owners. */
@Repository
@RequiredArgsConstructor
public class MyBatisPatientAccountOwnerDirectory implements PatientAccountOwnerDirectory {
  private static final String ACTIVE = "ACTIVE";

  private final PatientAccountOwnerMyBatisMapper mapper;

  @Override
  public Optional<PatientAccountOwner> lockForAccountLink(UUID patientId) {
    PatientAccountOwnerView row = mapper.lockById(patientId);
    if (row == null) return Optional.empty();
    return Optional.of(new PatientAccountOwner(row.id(), ACTIVE.equals(row.status()), row.phone()));
  }

  @Override
  public List<PatientOwnerCandidate> searchActive(String keyword, Integer limit) {
    return mapper.searchActive(keyword, likePattern(keyword), limit).stream()
        .map(
            row ->
                new PatientOwnerCandidate(
                    row.id(), row.patientCode(), row.fullName(), row.dateOfBirth(), row.phone()))
        .toList();
  }

  /** Builds a contains pattern in which user-typed LIKE wildcards are matched literally. */
  private static String likePattern(String keyword) {
    String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
