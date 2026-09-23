package com.ngockhanh.clinic.healthcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthcheck.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.BatchConfigurationLocked;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateEmployeeServiceAssignment;
import com.ngockhanh.clinic.healthcheck.domain.exception.PatientRelinkForbidden;
import com.ngockhanh.clinic.healthcheck.domain.exception.ServiceOutsideBatchScope;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatch;
import com.ngockhanh.clinic.healthcheck.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.BatchPriceRevision;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatchEmployee;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatchEmployeeService;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatchService;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckRecord;
import com.ngockhanh.clinic.healthcheck.domain.enums.HealthCheckRecordStatus;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckImportJob;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthCheckImportRow;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportType;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthCheckDomainTest {
    @Test
    void identificationNumberRemainsTextAndRejectsMalformedValues() {
        assertThat(IdentificationNumber.of("012345678901").value()).isEqualTo("012345678901");
        assertThatThrownBy(() -> IdentificationNumber.of("12A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentificationNumber.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rosterMemberCannotBeSilentlyRelinked() {
        CompanyEmployee employee = new CompanyEmployee(id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE");
        assertThat(employee.patientId()).isNull();
        employee.linkPatient(id(10));
        employee.linkPatient(10L);
        assertThatThrownBy(() -> employee.linkPatient(id(11))).isInstanceOf(PatientRelinkForbidden.class);
        assertThat(employee.patientId()).isEqualTo(id(10));
    }

    @Test
    void rosterReimportPreservesPatientLinkAndRejectsIdentityChange() {
        CompanyEmployee employee = new CompanyEmployee(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Old Name", LocalDate.of(1990, 1, 1), "MALE");
        employee.linkPatient(10L);
        CompanyEmployee updated = employee.reimport("E01", IdentificationNumber.of("012345678901"), "Corrected Name", LocalDate.of(1990, 1, 1), "MALE");
        assertThat(updated.patientId()).isEqualTo(id(10));
        assertThat(updated.fullName()).isEqualTo("Corrected Name");
        assertThatThrownBy(() -> employee.reimport("E01", IdentificationNumber.of("999999999999"), "Other", LocalDate.of(1990, 1, 1), "MALE"))
                .isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void batchFollowsDocumentedLifecycleAndReopenRequiresReason() {
        HealthCheckBatch batch = new HealthCheckBatch(id(7), id(1), "B01", new ExaminationSite("CLINIC", "Clinic", null), id(91));
        batch.addService(new HealthCheckBatchService(id(1), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1)));
        assertThatThrownBy(() -> batch.advanceTo(BatchStatus.FINALIZED)).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        batch.markReady();
        batch.advanceTo(BatchStatus.IN_PROGRESS);
        batch.advanceTo(BatchStatus.RESULT_PROCESSING);
        batch.advanceTo(BatchStatus.FINALIZED);
        batch.advanceTo(BatchStatus.CLOSED);
        assertThatThrownBy(() -> batch.reopenForRepricing(" ")).isInstanceOf(IllegalArgumentException.class);
        batch.reopenForRepricing("Contract correction");
        assertThat(batch.status()).isEqualTo(BatchStatus.RESULT_PROCESSING);
    }

    @Test
    void healthCheckRecordFollowsDocumentedLifecycle() {
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckRecord visit = HealthCheckRecord.prepare(ShsCode.of("SHS-5"), id(10), id(20), snapshot, LocalDate.of(2026, 9, 22), id(91));
        assertThat(visit.status()).isEqualTo(HealthCheckRecordStatus.ACTIVE);
        assertThatThrownBy(visit::complete).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        visit.checkIn(LocalDate.of(2026, 9, 22));
        visit.complete();
        assertThat(visit.status()).isEqualTo(HealthCheckRecordStatus.COMPLETED);
        assertThatThrownBy(visit::cancel).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void restoredAggregatesKeepPersistedGuards() {
        CompanyEmployee employee = CompanyEmployee.restore(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE", id(10));
        assertThatThrownBy(() -> employee.linkPatient(11L)).isInstanceOf(PatientRelinkForbidden.class);

        HealthCheckBatchService service = new HealthCheckBatchService(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1));
        HealthCheckBatch batch = HealthCheckBatch.restore(id(7), id(1), "B01", new ExaminationSite("CLINIC", "Clinic", null), id(91),
                null, null, BatchStatus.READY, List.of(service));
        assertThatThrownBy(() -> batch.addService(new HealthCheckBatchService(id(12), id(102), id(7), "S02", Money.vnd("100000"), Money.vnd("90000"), id(1))))
                .isInstanceOf(BatchConfigurationLocked.class);

        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckRecord record = HealthCheckRecord.restore(id(4), ShsCode.of("SHS-4"), id(10), id(20), id(3), snapshot,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22), id(91), HealthCheckRecordStatus.COMPLETED);
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 9, 22))).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);

        HealthCheckImportJob job = HealthCheckImportJob.restore(id(8), id(7), ImportType.EMPLOYEE_LIST,
                com.ngockhanh.clinic.healthcheck.domain.enums.ImportStatus.PARTIAL,
                List.of(new HealthCheckImportRow(2, "E01", snapshot), new HealthCheckImportRow(3, false, "MISSING_identificationNumber")));
        assertThat(job.confirmableRosterRows()).hasSize(1);

        HealthCheckBatchEmployeeService restoredAssignment = HealthCheckBatchEmployeeService.restore(id(11), id(101), Money.vnd("90000"), true);
        HealthCheckBatchEmployee participant = HealthCheckBatchEmployee.restore(id(3), id(7), snapshot, List.of(restoredAssignment));
        assertThat(participant.assignmentFor(id(11)).billable()).isTrue();
        assertThatThrownBy(() -> participant.assign(batch, checkedInRecord(id(3), snapshot), service, id(102)))
                .isInstanceOf(DuplicateEmployeeServiceAssignment.class);
    }

    @Test
    void batchScopeLocksAtReadyAndRejectsDuplicateService() {
        HealthCheckBatch batch = new HealthCheckBatch(id(7), id(1), "B01", new ExaminationSite("COMPANY", "Site", "Address"), id(91));
        HealthCheckBatchService service = new HealthCheckBatchService(id(1), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1));
        batch.addService(service);
        assertThatThrownBy(() -> batch.addService(service)).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        batch.markReady();
        assertThatThrownBy(() -> batch.addService(new HealthCheckBatchService(id(2), id(7), "S02", Money.vnd("100000"), Money.vnd("90000"), id(2)))).isInstanceOf(BatchConfigurationLocked.class);
    }

    @Test
    void preparedRecordChecksAgeAgainAtActualVisitAndPreservesShs() {
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(2008, 3, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckRecord record = HealthCheckRecord.prepare(ShsCode.of("SHS-1"), id(10), id(20), snapshot, LocalDate.of(2026, 3, 1), id(91));
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 2, 28))).isInstanceOf(AdultEligibilityViolation.class);
        record.checkIn(LocalDate.of(2026, 3, 1));
        record.checkIn(LocalDate.of(2026, 3, 1));
        assertThat(record.shs().value()).isEqualTo("SHS-1");
        assertThat(record.actualExaminationDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThatThrownBy(() -> record.checkIn(LocalDate.of(2026, 3, 2))).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void leapDayBirthDoesNotPassOnFebruaryTwentyEightInNonLeapYear() {
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(2008, 2, 29), "MALE", IdentificationNumber.of("012345678901"));
        assertThatThrownBy(() -> HealthCheckRecord.prepare(ShsCode.of("SHS-LEAP"), id(10), id(20), snapshot,
                LocalDate.of(2026, 2, 28), id(91))).isInstanceOf(AdultEligibilityViolation.class);
        assertThat(HealthCheckRecord.prepare(ShsCode.of("SHS-LEAP"), id(10), id(20), snapshot,
                LocalDate.of(2026, 3, 1), id(91))).isNotNull();
    }

    @Test
    void employeeCanOnlyReceiveServiceFromItsBatchAndBecomesBillableOnCompletion() {
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckBatchEmployee employee = new HealthCheckBatchEmployee(id(1), id(1), snapshot);
        HealthCheckBatch batch = new HealthCheckBatch(7L, 1L, "B01", new ExaminationSite("CLINIC", "Clinic", null), 91L);
        HealthCheckRecord checkedIn = checkedInRecord(id(1), snapshot);
        HealthCheckBatchService foreign = new HealthCheckBatchService(id(2), id(8), "S02", Money.vnd("100000"), Money.vnd("90000"), id(1));
        assertThatThrownBy(() -> employee.assign(batch, checkedIn, foreign, id(100))).isInstanceOf(ServiceOutsideBatchScope.class);
        HealthCheckBatchService uncontracted = new HealthCheckBatchService(id(2), id(7), "S02", Money.vnd("100000"), Money.vnd("90000"), id(1));
        assertThatThrownBy(() -> employee.assign(batch, checkedIn, uncontracted, id(100))).isInstanceOf(ServiceOutsideBatchScope.class);
        HealthCheckBatchService own = new HealthCheckBatchService(id(1), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1));
        batch.addService(own);
        batch.markReady();
        HealthCheckRecord prepared = HealthCheckRecord.prepare(id(5), ShsCode.of("SHS-PREP"), id(10), id(21), id(1), snapshot, LocalDate.of(2026, 9, 22), id(91));
        assertThatThrownBy(() -> employee.assign(batch, prepared, own, id(101))).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        employee.assign(batch, checkedIn, own, id(101));
        assertThatThrownBy(() -> employee.assign(batch, checkedIn, own, id(102))).isInstanceOf(DuplicateEmployeeServiceAssignment.class);
        assertThat(employee.assignmentFor(id(1)).billable()).isFalse();
        assertThatThrownBy(() -> employee.markBillable(id(101), false)).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        employee.markBillable(id(101), true);
        assertThat(employee.assignmentFor(id(1)).billable()).isTrue();
        assertThat(employee.assignmentFor(id(1)).unitPrice().amount()).isEqualByComparingTo(new BigDecimal("90000"));
    }

    @Test
    void importJobConfirmsValidRowsPartiallyAndRejectsDuplicateLineNumbers() {
        HealthCheckImportJob job = new HealthCheckImportJob(id(7), ImportType.EMPLOYEE_LIST);
        job.addRow(new HealthCheckImportRow(2, false, "MISSING_identificationNumber"));
        assertThatThrownBy(() -> job.addRow(new HealthCheckImportRow(2, true, null))).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        job.addRow(new HealthCheckImportRow(3, "E01", snapshot));
        job.validate();
        job.confirm();
        assertThat(job.status().name()).isEqualTo("PARTIAL");
        assertThat(job.confirmableRosterRows()).hasSize(1);
    }

    @Test
    void validRosterImportCanBeConfirmedWithoutPatientOrEncounter() {
        HealthCheckImportJob job = new HealthCheckImportJob(7L, ImportType.EMPLOYEE_LIST);
        job.addRow(new HealthCheckImportRow(2, "E01", new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"))));
        job.validate();
        job.confirm();
        assertThat(job.isConfirmed()).isTrue();
    }

    @Test
    void rosterImportCannotConfirmAValidFlagWithoutRosterData() {
        HealthCheckImportJob job = new HealthCheckImportJob(7L, ImportType.EMPLOYEE_LIST);
        job.addRow(new HealthCheckImportRow(2, true, null));
        job.validate();
        assertThatThrownBy(job::confirm).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void resultImportNeedsExactResolvedServiceRequest() {
        assertThatThrownBy(() -> HealthCheckImportRow.result(2, "E01", null, "LAB01", null))
                .isInstanceOf(IllegalArgumentException.class);
        HealthCheckImportJob job = new HealthCheckImportJob(id(7), ImportType.RESULTS);
        job.addRow(HealthCheckImportRow.result(2, "E01", null, "LAB01", id(301)));
        job.validate();
        job.confirm();
        assertThat(job.confirmableResultRows()).hasSize(1);
        assertThat(job.confirmableResultRows().getFirst().serviceRequestId()).isEqualTo(id(301));
    }

    @Test
    void batchPriceChangeIsCommonAndRequiresReason() {
        HealthCheckBatch batch = new HealthCheckBatch(7L, 1L, "B01", new ExaminationSite("CLINIC", "Clinic", null), 91L);
        HealthCheckBatchService service = new HealthCheckBatchService(1L, 7L, "S01", Money.vnd("100000"), Money.vnd("90000"), 1L);
        batch.addService(service);
        batch.markReady();
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckBatchEmployee employee = new HealthCheckBatchEmployee(1L, 1L, snapshot);
        employee.assign(batch, checkedInRecord(id(1), snapshot), service, id(101));
        assertThat(employee.assignmentFor(id(1)).unitPrice().amount()).isEqualByComparingTo("90000");
        assertThatThrownBy(() -> batch.repriceService(id(1), Money.vnd("120000"), " ")).isInstanceOf(IllegalArgumentException.class);
        BatchPriceRevision revision = batch.repriceService(id(1), Money.vnd("120000"), "Contract amendment");
        assertThat(batch.service(id(1)).negotiatedPrice().amount()).isEqualByComparingTo("120000");
        employee.applyPriceRevision(revision);
        assertThat(employee.assignmentFor(id(1)).unitPrice().amount()).isEqualByComparingTo("120000");
        assertThatThrownBy(() -> employee.applyPriceRevision(revision)).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void readyBatchRequiresAConfiguredServiceAndValidPlannedWindow() {
        assertThatThrownBy(() -> new HealthCheckBatch(id(7), id(1), "B01", new ExaminationSite("CLINIC", "Clinic", null), id(91),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1))).isInstanceOf(IllegalArgumentException.class);
        HealthCheckBatch batch = new HealthCheckBatch(id(7), id(1), "B01", new ExaminationSite("CLINIC", "Clinic", null), id(91),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2));
        assertThatThrownBy(batch::markReady).isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
    }

    @Test
    void batchRejectsDuplicateCatalogServiceEvenWithDifferentBatchServiceIds() {
        HealthCheckBatch batch = new HealthCheckBatch(7L, 1L, "B01", new ExaminationSite("CLINIC", "Clinic", null), 91L);
        batch.addService(new HealthCheckBatchService(id(11), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1)));
        assertThatThrownBy(() -> batch.addService(new HealthCheckBatchService(id(12), id(101), id(7), "S01", Money.vnd("100000"), Money.vnd("90000"), id(1))))
                .isInstanceOf(com.ngockhanh.clinic.healthcheck.domain.exception.DomainException.class);
        assertThat(batch.service(id(11)).serviceId()).isEqualTo(id(101));
    }

    @Test
    void snapshotRetainsOptionalPrintFields() {
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"),
                null, null, null, null, null, null, null, "Ha Noi", "Ward 1", "Street 1", "Nurse", "Company", "Annual check");
        HealthCheckRecord record = HealthCheckRecord.prepare(ShsCode.of("SHS-2"), id(10), id(20), snapshot, LocalDate.of(2026, 9, 22), id(91));
        assertThat(record.snapshot().workplaceOrSchool()).isEqualTo("Company");
        assertThat(record.snapshot().addressDetail()).isEqualTo("Street 1");
    }

    @Test
    void loadedAggregatesRetainIdentityAndCorporateVisitLink() {
        Company company = new Company(id(1), "C01", "Company", "Contact", "0900000000");
        CompanyEmployee employee = new CompanyEmployee(id(2), id(1), "E01", IdentificationNumber.of("012345678901"), "Nguyen A", LocalDate.of(1990, 1, 1), "MALE");
        AdministrativeSnapshot snapshot = new AdministrativeSnapshot("Nguyen A", LocalDate.of(1990, 1, 1), "MALE", IdentificationNumber.of("012345678901"));
        HealthCheckBatchEmployee participant = new HealthCheckBatchEmployee(id(3), id(2), snapshot);
        HealthCheckRecord visit = HealthCheckRecord.prepare(id(4), ShsCode.of("SHS-4"), id(10), id(20), id(3), snapshot, LocalDate.of(2026, 9, 22), id(91));
        assertThat(company.id()).isEqualTo(id(1));
        assertThat(employee.id()).isEqualTo(id(2));
        assertThat(participant.id()).isEqualTo(id(3));
        assertThat(visit.id()).isEqualTo(id(4));
        assertThat(visit.batchEmployeeId()).isEqualTo(id(3));
    }

    private static HealthCheckRecord checkedInRecord(long participantId, AdministrativeSnapshot snapshot) {
        HealthCheckRecord record = HealthCheckRecord.prepare(id(5), ShsCode.of("SHS-CHECKED"), id(10), id(20), participantId,
                snapshot, LocalDate.of(2026, 9, 22), id(91));
        record.checkIn(LocalDate.of(2026, 9, 22));
        return record;
    }
    private static UUID id(int value) {
        return new UUID(0L, value);
    }
}

