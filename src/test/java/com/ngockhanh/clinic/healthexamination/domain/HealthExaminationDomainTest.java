package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthexamination.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateParticipantServiceAssignment;
import com.ngockhanh.clinic.healthexamination.domain.exception.PatientRelinkForbidden;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationRecord;
import com.ngockhanh.clinic.healthexamination.domain.enums.HealthExaminationRecordStatus;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ShsCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthExaminationDomainTest {
    @Test
    void identificationNumberRemainsTextAndRejectsMalformedValues() {
        assertThat(IdentificationNumber.of("012345678901").value()).isEqualTo("012345678901");
        assertThatThrownBy(() -> IdentificationNumber.of("12A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentificationNumber.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyCccdValueObjectIsRemovedFromTheDomain() {
        assertThat(IdentificationNumber.class.getSimpleName()).isEqualTo("IdentificationNumber");
        assertThatThrownBy(() -> Class.forName("com.ngockhanh.clinic.healthexamination.domain.valueobject.Cccd"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void rosterMemberCannotBeSilentlyRelinked() {
        HealthExaminationParticipant employee = HealthExaminationParticipant.create(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE");
        assertThat(employee.patientId()).isNull();
        employee.linkPatient(id(10));
        employee.linkPatient(id(10));
        assertThatThrownBy(() -> employee.linkPatient(id(11))).isInstanceOf(PatientRelinkForbidden.class);
        assertThat(employee.patientId()).isEqualTo(id(10));
    }

    @Test
    void rosterReimportPreservesPatientLinkAndRejectsIdentityChange() {
        HealthExaminationParticipant employee = HealthExaminationParticipant.create(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Old Name", LocalDate.of(1990, 1, 1), "MALE");
        employee.linkPatient(id(10));
        HealthExaminationParticipant updated = employee.reimport("E01", IdentificationNumber.of("012345678901"), "Corrected Name", LocalDate.of(1990, 1, 1), "MALE");
        assertThat(updated.patientId()).isEqualTo(id(10));
        assertThat(updated.fullName()).isEqualTo("Corrected Name");
        assertThatThrownBy(() -> employee.reimport("E01", IdentificationNumber.of("999999999999"), "Other", LocalDate.of(1990, 1, 1), "MALE"))
                .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void batchFollowsDocumentedLifecycleAndReopenRequiresReason() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        batch.addService(HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1)));
        assertThatThrownBy(batch::start).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
        batch.markReady();
        batch.start();
        batch.startResultProcessing();
        java.time.Instant finalizedAt = java.time.Instant.parse("2026-09-23T10:00:00Z");
        java.time.Instant closedAt = java.time.Instant.parse("2026-09-23T11:00:00Z");
        batch.finalizeBatch(finalizedAt);
        assertThat(batch.finalizedAt()).isEqualTo(finalizedAt);
        batch.close(closedAt);
        assertThat(batch.closedAt()).isEqualTo(closedAt);
        assertThatThrownBy(() -> batch.reopenForRepricing(" ")).isInstanceOf(IllegalArgumentException.class);
        batch.reopenForRepricing("Contract correction");
        assertThat(batch.status()).isEqualTo(BatchStatus.RESULT_PROCESSING);
        assertThat(batch.finalizedAt()).isNull();
        assertThat(batch.closedAt()).isNull();
    }

    @Test
    void restoredBatchRequiresLifecycleTimestampsToMatchStatus() {
        ExaminationSite site = new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null);
        List<HealthExaminationBatchService> services = List.of(
                HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1)));

        assertThatThrownBy(() -> HealthExaminationBatch.restore(batchId(7), id(1), "B01", site, id(91), null, null,
                BatchStatus.FINALIZED, services, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HealthExaminationBatch.restore(batchId(7), id(1), "B01", site, id(91), null, null,
                BatchStatus.CLOSED, services, java.time.Instant.parse("2026-09-23T10:00:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class);

        java.time.Instant finalizedAt = java.time.Instant.parse("2026-09-23T10:00:00Z");
        java.time.Instant closedAt = java.time.Instant.parse("2026-09-23T11:00:00Z");
        HealthExaminationBatch restoredFinalized = HealthExaminationBatch.restore(batchId(7), id(1), "B01", site, id(91), null, null,
                BatchStatus.FINALIZED, services, finalizedAt, null);
        HealthExaminationBatch restoredClosed = HealthExaminationBatch.restore(batchId(7), id(1), "B01", site, id(91), null, null,
                BatchStatus.CLOSED, services, finalizedAt, closedAt);
        assertThat(restoredFinalized.finalizedAt()).isEqualTo(finalizedAt);
        assertThat(restoredFinalized.closedAt()).isNull();
        assertThat(restoredClosed.finalizedAt()).isEqualTo(finalizedAt);
        assertThat(restoredClosed.closedAt()).isEqualTo(closedAt);
    }

    @Test
    void batchCanBeCanceledOnlyBeforeFinalizationAndRequiresReason() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(
                ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        assertThatThrownBy(() -> batch.cancel(" ")).isInstanceOf(IllegalArgumentException.class);
        batch.cancel("Customer canceled the visit");
        assertThat(batch.status()).isEqualTo(BatchStatus.CANCELED);
        HealthExaminationBatch restoredCanceled = HealthExaminationBatch.restore(batchId(7), id(1), "B01", new ExaminationSite(
                ExaminationSiteType.CLINIC, "Clinic", null), id(91),
                null, null, BatchStatus.CANCELED, List.of());
        assertThat(restoredCanceled.status()).isEqualTo(BatchStatus.CANCELED);

        HealthExaminationBatch finalized = HealthExaminationBatch.create(batchId(8), id(1), "B02", new ExaminationSite(
                ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        finalized.addService(HealthExaminationBatchService.create(id(12), id(102), id(8), "S02",
                Money.vnd("100000"), Money.vnd("90000"), id(1)));
        finalized.markReady();
        finalized.start();
        finalized.startResultProcessing();
        finalized.finalizeBatch(java.time.Instant.parse("2026-09-23T10:00:00Z"));
        assertThatThrownBy(() -> finalized.cancel("Late cancel"))
                .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void healthExaminationRecordFollowsDocumentedLifecycle() {
        HealthExaminationRecord visit = preparedRecord(id(30), ShsCode.of("SHS-5"), LocalDate.of(1990, 1, 1), LocalDate.of(2026, 9, 22));
        assertThat(visit.status()).isEqualTo(HealthExaminationRecordStatus.ACTIVE);
        assertThatThrownBy(visit::complete).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
        visit.checkIn(LocalDate.of(2026, 9, 22));
        visit.complete();
        assertThat(visit.status()).isEqualTo(HealthExaminationRecordStatus.COMPLETED);
        assertThatThrownBy(visit::cancel).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void restoredAggregatesKeepPersistedGuards() {
        HealthExaminationParticipant employee = HealthExaminationParticipant.restore(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE", id(10));
        assertThatThrownBy(() -> employee.linkPatient(id(11))).isInstanceOf(PatientRelinkForbidden.class);

        HealthExaminationBatch batch = HealthExaminationBatch.restore(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91),
                null, null, BatchStatus.READY, List.of(HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1))));
        assertThatThrownBy(() -> batch.addService(HealthExaminationBatchService.create(id(12), id(102), id(7), "S02", Money.vnd("100000"), Money.vnd("90000"), id(1))))
                .isInstanceOf(BatchConfigurationLocked.class);

        HealthExaminationRecord record = restoredBatchRecord(id(4), ShsCode.of("SHS-4"),
                LocalDate.of(1990, 1, 1), LocalDate.of(2026, 9, 22));
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 9, 22))).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);

        HealthExaminationImportJob job = HealthExaminationImportJob.restore(id(8), id(7), ImportType.PARTICIPANT_LIST,
                com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus.PARTIAL,
                List.of(rosterRow(id(102), 2), HealthExaminationImportRow.invalid(id(103), 3, "MISSING_IDENTIFICATION_NUMBER")));
        assertThat(job.confirmableRosterRows()).hasSize(1);
        assertThat(job.confirmableRosterRows().getFirst().id()).isEqualTo(id(102));

        HealthExaminationBatchParticipantService restoredAssignment = HealthExaminationBatchParticipantService.restore(id(20), id(11), id(101), Money.vnd("90000"), true);
        HealthExaminationBatchParticipant participant = participant(batchParticipantId(3), List.of(restoredAssignment));
        assertThat(participant.assignmentFor(id(11)).billable()).isTrue();
        assertThatThrownBy(() -> participant.assignService(id(21), id(11), id(102), Money.vnd("90000")))
                .isInstanceOf(DuplicateParticipantServiceAssignment.class);
    }
    @Test
    void batchScopeLocksAtReadyAndRejectsDuplicateService() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.COMPANY, "Site", "Address"), id(91));
        HealthExaminationBatchService service = HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1));
        batch.addService(service);
        assertThatThrownBy(() -> batch.addService(service)).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
        batch.markReady();
        assertThatThrownBy(() -> batch.addService(HealthExaminationBatchService.create(id(12), id(102), id(7), "S02", Money.vnd("100000"), Money.vnd("90000"), id(2)))).isInstanceOf(BatchConfigurationLocked.class);
    }

    @Test
    void preparedRecordChecksAgeAgainAtActualVisitAndPreservesShs() {
        HealthExaminationRecord record = preparedRecord(id(30), ShsCode.of("SHS-1"),
                LocalDate.of(2008, 3, 1), LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 2, 28))).isInstanceOf(AdultEligibilityViolation.class);
        record.checkIn(LocalDate.of(2026, 3, 1));
        record.checkIn(LocalDate.of(2026, 3, 1));
        assertThat(record.shs().value()).isEqualTo("SHS-1");
        assertThat(record.actualExaminationDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 3, 2))).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void leapDayBirthDoesNotPassOnFebruaryTwentyEightInNonLeapYear() {
        assertThatThrownBy(() -> preparedRecord(id(30), ShsCode.of("SHS-LEAP"),
                LocalDate.of(2008, 2, 29), LocalDate.of(2026, 2, 28))).isInstanceOf(AdultEligibilityViolation.class);
        assertThat(preparedRecord(id(30), ShsCode.of("SHS-LEAP"),
                LocalDate.of(2008, 2, 29), LocalDate.of(2026, 3, 1))).isNotNull();
    }

    @Test
    void employeeAssignmentOwnsOnlyLocalAssignmentInvariants() {
        HealthExaminationBatchParticipant employee = participant(batchParticipantId(1), List.of());

        employee.assignService(id(20), id(11), id(101), Money.vnd("90000"));
        assertThat(employee.assignmentFor(id(11)).id()).isEqualTo(id(20));
        assertThatThrownBy(() -> employee.assignService(id(21), sameId(id(11)), id(102), Money.vnd("90000")))
                .isInstanceOf(DuplicateParticipantServiceAssignment.class);
        assertThat(employee.assignmentFor(sameId(id(11))).billable()).isFalse();

        employee.markServiceBillable(sameId(id(101)));
        employee.markServiceBillable(id(101));
        assertThat(employee.assignmentFor(id(11)).billable()).isTrue();
        assertThat(employee.assignmentFor(id(11)).unitPrice().amount()).isEqualByComparingTo(new BigDecimal("90000"));
    }

    @Test
    void participantServiceAssignmentCanBeLinkedToARequestLater() {
        HealthExaminationBatchParticipant participant = participant(batchParticipantId(22), List.of());

        participant.assignService(id(20), id(11), Money.vnd("90000"));
        assertThat(participant.assignmentFor(id(11)).serviceRequestId()).isNull();

        participant.linkServiceRequest(id(11), id(301));
        participant.linkServiceRequest(sameId(id(11)), sameId(id(301)));
        assertThat(participant.assignmentFor(id(11)).serviceRequestId()).isEqualTo(id(301));
        assertThatThrownBy(() -> participant.linkServiceRequest(id(11), id(302)))
                .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }
    @Test
    void importJobConfirmsValidRowsPartiallyAndRejectsDuplicateLineNumbers() {
        HealthExaminationImportJob job = HealthExaminationImportJob.create(id(8), id(7), ImportType.PARTICIPANT_LIST);
        job.addRow(HealthExaminationImportRow.invalid(id(102), 2, "MISSING_IDENTIFICATION_NUMBER"));
        assertThatThrownBy(() -> job.addRow(HealthExaminationImportRow.invalid(id(102), 2, "DUPLICATE_ROW"))).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
        job.addRow(rosterRow(id(103), 3));
        job.validate();
        job.confirm();
        assertThat(job.status().name()).isEqualTo("PARTIAL");
        assertThat(job.confirmableRosterRows()).hasSize(1);
        assertThat(job.confirmableRosterRows().getFirst().id()).isEqualTo(id(103));
    }

    @Test
    void validRosterImportCanBeConfirmedWithoutPatientOrEncounter() {
        HealthExaminationImportJob job = HealthExaminationImportJob.create(id(8), id(7), ImportType.PARTICIPANT_LIST);
        HealthExaminationImportRow rosterRow = rosterRow(id(102), 2);
        assertThat(rosterRow.identificationNumber()).isEqualTo(IdentificationNumber.of("012345678901"));
        job.addRow(rosterRow);
        job.validate();
        job.confirm();
        assertThat(job.isConfirmed()).isTrue();
    }

    @Test
    void rosterImportCannotConfirmAValidFlagWithoutRosterData() {
        HealthExaminationImportJob job = HealthExaminationImportJob.create(id(8), id(7), ImportType.PARTICIPANT_LIST);
        job.addRow(HealthExaminationImportRow.invalid(id(102), 2, "DUPLICATE_ROW"));
        job.validate();
        assertThatThrownBy(job::confirm).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void resultImportNeedsExactResolvedServiceRequest() {
        assertThatThrownBy(() -> HealthExaminationImportRow.result(id(102), 2, "E01", null, "LAB01", null))
                .isInstanceOf(IllegalArgumentException.class);
        HealthExaminationImportJob job = HealthExaminationImportJob.create(id(8), id(7), ImportType.RESULTS);
        job.addRow(HealthExaminationImportRow.result(id(102), 2, "E01", null, "LAB01", id(301)));
        job.validate();
        job.confirm();
        assertThat(job.confirmableResultRows()).hasSize(1);
        assertThat(job.confirmableResultRows().getFirst().serviceRequestId()).isEqualTo(id(301));
    }

    @Test
    void batchPriceChangeIsCommonAndRequiresReason() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        HealthExaminationBatchService service = HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1));
        batch.addService(service);
        batch.markReady();
        HealthExaminationBatchParticipant employee = participant(batchParticipantId(1), List.of());
        employee.assignService(id(20), id(11), id(101), service.negotiatedPrice());
        assertThat(employee.assignmentFor(id(11)).unitPrice().amount()).isEqualByComparingTo("90000");
        assertThatThrownBy(() -> batch.repriceService(id(11), Money.vnd("120000"), " ")).isInstanceOf(IllegalArgumentException.class);
        BatchPriceRevision revision = batch.repriceService(id(11), Money.vnd("120000"), "Contract amendment");
        assertThat(batch.service(id(11)).negotiatedPrice().amount()).isEqualByComparingTo("120000");
        employee.applyPriceRevision(revision);
        assertThat(employee.assignmentFor(id(11)).unitPrice().amount()).isEqualByComparingTo("120000");
        assertThatThrownBy(() -> employee.applyPriceRevision(revision)).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }
    @Test
    void readyBatchRequiresAConfiguredServiceAndValidPlannedWindow() {
        assertThatThrownBy(() -> HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1))).isInstanceOf(IllegalArgumentException.class);
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        assertThatThrownBy(batch::markReady).isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void batchRejectsDuplicateCatalogServiceEvenWithDifferentBatchServiceIds() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01", new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        batch.addService(HealthExaminationBatchService.create(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1)));
        assertThatThrownBy(() -> batch.addService(HealthExaminationBatchService.create(id(12), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1))))
                .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
        assertThat(batch.service(id(11)).serviceId()).isEqualTo(id(101));
    }

    @Test
    void snapshotRetainsOptionalPrintFields() {
        HealthExaminationRecord record = HealthExaminationRecord.prepare(id(30), ShsCode.of("SHS-2"), id(10), id(20), null,
                "Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"),
                null, null, null, null, null, null, null, "Ha Noi", "Ward 1", "Street 1", "Nurse",
                "Company", "Annual check", LocalDate.of(2026, 9, 22), id(91));
        assertThat(record.workplaceOrSchoolSnapshot()).isEqualTo("Company");
        assertThat(record.addressDetailSnapshot()).isEqualTo("Street 1");
    }

    @Test
    void loadedAggregatesRetainIdentityAndCorporateVisitLink() {
        Organization company = Organization.create(id(1), "Organization", "Contact", "0900000000");
        HealthExaminationParticipant employee = HealthExaminationParticipant.create(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE");
        HealthExaminationBatchParticipant participant = HealthExaminationBatchParticipant.restore(
                batchParticipantId(3), id(7), id(2), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE",
                IdentificationNumber.of("012345678901"), List.of());
        HealthExaminationRecord visit = preparedBatchRecord(id(4), ShsCode.of("SHS-4"),
                LocalDate.of(1990, 1, 1), LocalDate.of(2026, 9, 22));
        assertThat(company.id()).isEqualTo(id(1));
        assertThat(employee.id()).isEqualTo(id(2));
        assertThat(participant.id()).isEqualTo(id(3));
        assertThat(participant.healthExaminationParticipantId()).isEqualTo(id(2));
        assertThat(visit.id()).isEqualTo(id(4));
        assertThat(visit.batchParticipantId()).isEqualTo(id(3));
    }

    @Test
    void linkedEmployeeComparesPatientUuidByValue() {
        HealthExaminationParticipant employee = HealthExaminationParticipant.create(id(2), id(1), "E01", IdentificationNumber.of("012345678901"),
                "Nguyen A", LocalDate.of(1990, 1, 1), "MALE");
        AggregateId patientId = id(10);
        employee.linkPatient(patientId);

        employee.linkPatient(sameId(patientId));

        assertThat(employee.patientId()).isEqualTo(patientId);
    }

    @Test
    void batchScopeComparesBatchUuidByValue() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01",
                new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        HealthExaminationBatchService service = HealthExaminationBatchService.create(id(11), id(101), batch.id(), "S01",
                Money.vnd("100000"), Money.vnd("90000"), id(1));

        batch.addService(service);

        assertThat(batch.service(id(11))).isSameAs(service);
    }

    @Test
    void batchRejectsCatalogServiceDuplicateByUuidValue() {
        HealthExaminationBatch batch = HealthExaminationBatch.create(batchId(7), id(1), "B01",
                new ExaminationSite(ExaminationSiteType.CLINIC, "Clinic", null), id(91));
        batch.addService(HealthExaminationBatchService.create(id(11), id(101), batch.id(), "S01",
                Money.vnd("100000"), Money.vnd("90000"), id(1)));

        assertThatThrownBy(() -> batch.addService(HealthExaminationBatchService.create(id(12), sameId(id(101)), batch.id(), "S01",
                Money.vnd("100000"), Money.vnd("90000"), id(1))))
                .isInstanceOf(com.ngockhanh.clinic.healthexamination.domain.exception.DomainException.class);
    }

    @Test
    void employeeAssignmentUsesBatchServiceUuidValueAsChildIdentity() {
        HealthExaminationBatchParticipant participant = participant(batchParticipantId(21), List.of());
        AggregateId batchServiceId = id(11);

        participant.assignService(id(20), batchServiceId, id(301), Money.vnd("90000"));

        assertThat(participant.assignmentFor(sameId(batchServiceId))).isNotNull();
    }
    @Test
    void billableLookupComparesServiceRequestUuidByValue() {
        HealthExaminationBatchParticipant participant = participant(batchParticipantId(21), List.of());
        AggregateId serviceRequestId = id(301);
        participant.assignService(id(20), id(11), serviceRequestId, Money.vnd("90000"));

        participant.markServiceBillable(sameId(serviceRequestId));

        assertThat(participant.assignmentFor(id(11)).billable()).isTrue();
    }
    @Test
    void priceRevisionComparesBatchUuidByValue() {
        HealthExaminationBatchParticipant participant = participant(batchParticipantId(21), List.of());
        BatchPriceRevision revision = new BatchPriceRevision(sameId(id(7)), id(11), Money.vnd("90000"),
                Money.vnd("100000"), "Contract amendment");

        participant.applyPriceRevision(revision);
        assertThat(participant.id()).isEqualTo(id(21));
    }
    private static AggregateId batchId(int value) {
        return id(value);
    }
    private static AggregateId batchParticipantId(int value) {
        return id(value);
    }
    private static AggregateId sameId(AggregateId value) {
        return AggregateId.of(UUID.fromString(value.value().toString()));
    }
    private static HealthExaminationBatchParticipant participant(AggregateId id,
                                                                  List<HealthExaminationBatchParticipantService> assignments) {
        return HealthExaminationBatchParticipant.restore(id, id(7), id(8), "Nguyen A",
                LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"), assignments);
    }
    private static HealthExaminationImportRow rosterRow(AggregateId id, int rowNumber) {
        return HealthExaminationImportRow.roster(id, rowNumber, "E01", "Nguyen A",
                LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
    }
    private static HealthExaminationRecord preparedRecord(AggregateId id, ShsCode shs,
                                                           LocalDate birthDate, LocalDate plannedDate) {
        return HealthExaminationRecord.prepare(id, shs, id(10), id(20), "Nguyen A", birthDate,
                "MALE", IdentificationNumber.of("012345678901"), plannedDate, id(91));
    }
    private static HealthExaminationRecord preparedBatchRecord(AggregateId id, ShsCode shs,
                                                                LocalDate birthDate, LocalDate plannedDate) {
        return HealthExaminationRecord.prepare(id, shs, id(10), id(20), id(3), "Nguyen A", birthDate,
                "MALE", IdentificationNumber.of("012345678901"), null, null, null, null, null, null, null,
                null, null, null, null, null, null, plannedDate, id(91));
    }
    private static HealthExaminationRecord restoredBatchRecord(AggregateId id, ShsCode shs,
                                                                LocalDate birthDate, LocalDate plannedDate) {
        return HealthExaminationRecord.restore(id, shs, id(10), id(20), id(3), "Nguyen A", birthDate,
                "MALE", IdentificationNumber.of("012345678901"), plannedDate, plannedDate, id(91),
                HealthExaminationRecordStatus.COMPLETED);
    }
    private static AggregateId id(int value) {
        return AggregateId.of(new UUID(0L, value));
    }
}

