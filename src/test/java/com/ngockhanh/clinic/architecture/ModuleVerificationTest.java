package com.ngockhanh.clinic.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.NgocKhanhClinicApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleVerificationTest {
    private final JavaClasses applicationClasses = new ClassFileImporter().importPackages("com.ngockhanh.clinic");

    @Test
    void modulesRespectTheirPublishedBoundaries() {
        ApplicationModules.of(NgocKhanhClinicApplication.class).verify();
    }

    @Test
    void healthCheckHasOnlyTheSixDocumentedAggregateRootsAndTheirRepositories() {
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().equals("com.ngockhanh.clinic.healthcheck.domain.aggregate"))
                .filter(type -> !type.getSimpleName().isBlank())
                .map(type -> type.getSimpleName())
                .toList())
                .containsExactlyInAnyOrder("Company", "CompanyEmployee", "HealthCheckBatch",
                        "HealthCheckBatchEmployee", "HealthCheckRecord", "HealthCheckImportJob");
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().equals("com.ngockhanh.clinic.healthcheck.domain.repository"))
                .map(type -> type.getSimpleName())
                .toList())
                .containsExactlyInAnyOrder("CompanyRepository", "CompanyEmployeeRepository", "HealthCheckBatchRepository",
                        "HealthCheckBatchEmployeeRepository", "HealthCheckRecordRepository", "HealthCheckImportJobRepository");
    }
    @Test
    void domainDoesNotDependOnFrameworkPersistenceOrTransportTypes() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.apache.ibatis..", "com.ngockhanh.clinic..infrastructure..",
                        "com.ngockhanh.clinic..api.request..", "com.ngockhanh.clinic..api.response..")
                .check(applicationClasses);
    }

    @Test
    void apiAndApplicationDoNotDependOnMyBatisOrPersistenceRecords() {
        noClasses().that().resideInAnyPackage("com.ngockhanh.clinic..api..", "com.ngockhanh.clinic..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.apache.ibatis..", "com.ngockhanh.clinic..infrastructure.persistence.mapper..",
                        "com.ngockhanh.clinic..infrastructure.persistence.record..")
                .check(applicationClasses);
    }

    @Test
    void apiDoesNotCallDomainOrInfrastructureDirectly() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ngockhanh.clinic..domain..", "com.ngockhanh.clinic..infrastructure..")
                .allowEmptyShould(true)
                .check(applicationClasses);
    }
}
