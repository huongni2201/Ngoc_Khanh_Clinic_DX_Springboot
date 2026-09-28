package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateParticipantServiceAssignment;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public final class HealthExaminationBatchParticipant {
	private final AggregateId id;
	private final AggregateId batchId;
	private final AggregateId healthExaminationParticipantId;
	private final String participantCodeSnapshot;
	private final String departmentSnapshot;
	private final String jobTitleSnapshot;
	private final String occupationSnapshot;
	private final String fullNameSnapshot;
	private final LocalDate dateOfBirthSnapshot;
	private final String sexSnapshot;
	private final IdentificationNumber identificationNumberSnapshot;
	private final LocalDate identificationNumberIssueDateSnapshot;
	private final String identificationNumberIssuePlaceSnapshot;
	private final String ethnicitySnapshot;
	private final String subjectTypeSnapshot;
	private final String payerSourceSnapshot;
	private final String bloodGroupSnapshot;
	private final String phoneSnapshot;
	private final String provinceSnapshot;
	private final String wardSnapshot;
	private final String addressDetailSnapshot;
	private final String administrativeOccupationSnapshot;
	private final String workplaceOrSchoolSnapshot;
	private final String healthExaminationReasonSnapshot;
	private final Map<AggregateId, HealthExaminationBatchParticipantService> assignments = new HashMap<>();

	private HealthExaminationBatchParticipant(
			AggregateId id, AggregateId batchId,
			AggregateId healthExaminationParticipantId,
			String participantCodeSnapshot, String departmentSnapshot,
			String jobTitleSnapshot, String occupationSnapshot,
			String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
			String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
			LocalDate identificationNumberIssueDateSnapshot,
			String identificationNumberIssuePlaceSnapshot, String ethnicitySnapshot,
			String subjectTypeSnapshot, String payerSourceSnapshot,
			String bloodGroupSnapshot, String phoneSnapshot, String provinceSnapshot,
			String wardSnapshot, String addressDetailSnapshot,
			String administrativeOccupationSnapshot, String workplaceOrSchoolSnapshot,
			String healthExaminationReasonSnapshot) {
		if (id == null || batchId == null || healthExaminationParticipantId == null
				|| fullNameSnapshot == null || fullNameSnapshot.isBlank() || dateOfBirthSnapshot == null
				|| sexSnapshot == null || sexSnapshot.isBlank() || identificationNumberSnapshot == null) {
			throw new IllegalArgumentException("Invalid health-examination batch participant");
		}
		this.id = id;
		this.batchId = batchId;
		this.healthExaminationParticipantId = healthExaminationParticipantId;
		this.participantCodeSnapshot = participantCodeSnapshot;
		this.departmentSnapshot = departmentSnapshot;
		this.jobTitleSnapshot = jobTitleSnapshot;
		this.occupationSnapshot = occupationSnapshot;
		this.fullNameSnapshot = fullNameSnapshot;
		this.dateOfBirthSnapshot = dateOfBirthSnapshot;
		this.sexSnapshot = sexSnapshot;
		this.identificationNumberSnapshot = identificationNumberSnapshot;
		this.identificationNumberIssueDateSnapshot = identificationNumberIssueDateSnapshot;
		this.identificationNumberIssuePlaceSnapshot = identificationNumberIssuePlaceSnapshot;
		this.ethnicitySnapshot = ethnicitySnapshot;
		this.subjectTypeSnapshot = subjectTypeSnapshot;
		this.payerSourceSnapshot = payerSourceSnapshot;
		this.bloodGroupSnapshot = bloodGroupSnapshot;
		this.phoneSnapshot = phoneSnapshot;
		this.provinceSnapshot = provinceSnapshot;
		this.wardSnapshot = wardSnapshot;
		this.addressDetailSnapshot = addressDetailSnapshot;
		this.administrativeOccupationSnapshot = administrativeOccupationSnapshot;
		this.workplaceOrSchoolSnapshot = workplaceOrSchoolSnapshot;
		this.healthExaminationReasonSnapshot = healthExaminationReasonSnapshot;
	}

	public static HealthExaminationBatchParticipant create(
			AggregateId id, AggregateId batchId,
			AggregateId healthExaminationParticipantId,
			String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
			String sexSnapshot,
			IdentificationNumber identificationNumberSnapshot) {
		return create(id, batchId, healthExaminationParticipantId, null, null, null, null,
				fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot,
				null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	public static HealthExaminationBatchParticipant create(
			AggregateId id, AggregateId batchId,
			AggregateId healthExaminationParticipantId,
			String participantCodeSnapshot, String departmentSnapshot,
			String jobTitleSnapshot, String occupationSnapshot,
			String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
			String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
			LocalDate identificationNumberIssueDateSnapshot,
			String identificationNumberIssuePlaceSnapshot,
			String ethnicitySnapshot, String subjectTypeSnapshot,
			String payerSourceSnapshot, String bloodGroupSnapshot,
			String phoneSnapshot, String provinceSnapshot,
			String wardSnapshot, String addressDetailSnapshot,
			String administrativeOccupationSnapshot,
			String workplaceOrSchoolSnapshot,
			String healthExaminationReasonSnapshot) {
		return new HealthExaminationBatchParticipant(id, batchId, healthExaminationParticipantId,
				participantCodeSnapshot, departmentSnapshot, jobTitleSnapshot, occupationSnapshot,
				fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot,
				identificationNumberIssueDateSnapshot, identificationNumberIssuePlaceSnapshot, ethnicitySnapshot,
				subjectTypeSnapshot, payerSourceSnapshot, bloodGroupSnapshot, phoneSnapshot, provinceSnapshot,
				wardSnapshot, addressDetailSnapshot, administrativeOccupationSnapshot, workplaceOrSchoolSnapshot,
				healthExaminationReasonSnapshot);
	}

	public static HealthExaminationBatchParticipant restore(
			AggregateId id, AggregateId batchId,
			AggregateId healthExaminationParticipantId,
			String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
			String sexSnapshot,
			IdentificationNumber identificationNumberSnapshot,
			List<HealthExaminationBatchParticipantService> assignments) {
		return restore(id, batchId, healthExaminationParticipantId, null, null, null, null,
				fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot,
				null, null, null, null, null, null, null, null, null, null, null, null, null, assignments);
	}

	public static HealthExaminationBatchParticipant restore(
			AggregateId id, AggregateId batchId,
			AggregateId healthExaminationParticipantId,
			String participantCodeSnapshot, String departmentSnapshot,
			String jobTitleSnapshot, String occupationSnapshot,
			String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
			String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
			LocalDate identificationNumberIssueDateSnapshot,
			String identificationNumberIssuePlaceSnapshot,
			String ethnicitySnapshot, String subjectTypeSnapshot,
			String payerSourceSnapshot, String bloodGroupSnapshot,
			String phoneSnapshot, String provinceSnapshot,
			String wardSnapshot, String addressDetailSnapshot,
			String administrativeOccupationSnapshot,
			String workplaceOrSchoolSnapshot,
			String healthExaminationReasonSnapshot,
			List<HealthExaminationBatchParticipantService> assignments) {
		if (assignments == null) throw new IllegalArgumentException("Invalid persisted batch participant");
		HealthExaminationBatchParticipant participant = create(id, batchId, healthExaminationParticipantId,
				participantCodeSnapshot, departmentSnapshot, jobTitleSnapshot, occupationSnapshot,
				fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot,
				identificationNumberIssueDateSnapshot, identificationNumberIssuePlaceSnapshot, ethnicitySnapshot,
				subjectTypeSnapshot, payerSourceSnapshot, bloodGroupSnapshot, phoneSnapshot, provinceSnapshot,
				wardSnapshot, addressDetailSnapshot, administrativeOccupationSnapshot, workplaceOrSchoolSnapshot,
				healthExaminationReasonSnapshot);
		for (HealthExaminationBatchParticipantService assignment : assignments) {
			if (assignment == null || participant.assignments.putIfAbsent(assignment.batchServiceId(), assignment) != null) {
				throw new IllegalArgumentException("Invalid persisted assignment list");
			}
		}
		return participant;
	}

	public void assignService(AggregateId assignmentId, AggregateId batchServiceId,
	                          AggregateId serviceRequestId, Money negotiatedPrice) {
		if (assignmentId == null || batchServiceId == null || serviceRequestId == null || negotiatedPrice == null) {
			throw new IllegalArgumentException("Incomplete participant service assignment");
		}
		if (assignments.putIfAbsent(batchServiceId,
				HealthExaminationBatchParticipantService.create(assignmentId, batchServiceId,
						serviceRequestId, negotiatedPrice)) != null) {
			throw new DuplicateParticipantServiceAssignment();
		}
	}

	public void markServiceBillable(AggregateId serviceRequestId) {
		if (serviceRequestId == null) throw new IllegalArgumentException("Missing Service Request");
		Map.Entry<AggregateId, HealthExaminationBatchParticipantService> entry = assignments.entrySet().stream()
				.filter(item -> Objects.equals(item.getValue().serviceRequestId(), serviceRequestId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown Service Request"));
		assignments.put(entry.getKey(), entry.getValue().markedBillable());
	}

	public void applyPriceRevision(BatchPriceRevision revision) {
		if (revision == null || !Objects.equals(revision.batchId(), batchId)) {
			throw new IllegalArgumentException("Price revision for another batch");
		}
		HealthExaminationBatchParticipantService assignment = assignments.get(revision.batchServiceId());
		if (assignment != null) {
			if (assignment.unitPrice().amount().compareTo(revision.oldPrice().amount()) != 0) {
				throw new DomainRuleViolation("Stale batch price revision");
			}
			assignments.put(revision.batchServiceId(), assignment.withUnitPrice(revision.newPrice()));
		}
	}

	public HealthExaminationBatchParticipantService assignmentFor(AggregateId batchServiceId) {
		return assignments.get(batchServiceId);
	}

	public AggregateId id() {
		return id;
	}

	public AggregateId batchId() {
		return batchId;
	}

	public AggregateId healthExaminationParticipantId() {
		return healthExaminationParticipantId;
	}

	public String participantCodeSnapshot() {
		return participantCodeSnapshot;
	}

	public String departmentSnapshot() {
		return departmentSnapshot;
	}

	public String jobTitleSnapshot() {
		return jobTitleSnapshot;
	}

	public String occupationSnapshot() {
		return occupationSnapshot;
	}

	public String fullNameSnapshot() {
		return fullNameSnapshot;
	}

	public LocalDate dateOfBirthSnapshot() {
		return dateOfBirthSnapshot;
	}

	public String sexSnapshot() {
		return sexSnapshot;
	}

	public IdentificationNumber identificationNumberSnapshot() {
		return identificationNumberSnapshot;
	}

	public LocalDate identificationNumberIssueDateSnapshot() {
		return identificationNumberIssueDateSnapshot;
	}

	public String identificationNumberIssuePlaceSnapshot() {
		return identificationNumberIssuePlaceSnapshot;
	}

	public String ethnicitySnapshot() {
		return ethnicitySnapshot;
	}

	public String subjectTypeSnapshot() {
		return subjectTypeSnapshot;
	}

	public String payerSourceSnapshot() {
		return payerSourceSnapshot;
	}

	public String bloodGroupSnapshot() {
		return bloodGroupSnapshot;
	}

	public String phoneSnapshot() {
		return phoneSnapshot;
	}

	public String provinceSnapshot() {
		return provinceSnapshot;
	}

	public String wardSnapshot() {
		return wardSnapshot;
	}

	public String addressDetailSnapshot() {
		return addressDetailSnapshot;
	}

	public String administrativeOccupationSnapshot() {
		return administrativeOccupationSnapshot;
	}

	public String workplaceOrSchoolSnapshot() {
		return workplaceOrSchoolSnapshot;
	}

	public String healthExaminationReasonSnapshot() {
		return healthExaminationReasonSnapshot;
	}

	public List<HealthExaminationBatchParticipantService> assignments() {
		return List.copyOf(assignments.values());
	}
}
