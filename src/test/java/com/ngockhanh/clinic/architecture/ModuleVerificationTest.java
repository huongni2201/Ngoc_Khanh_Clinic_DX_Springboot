package com.ngockhanh.clinic.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.NgocKhanhClinicApplication;
import com.ngockhanh.clinic.identity.application.port.SessionRevocation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleVerificationTest {
    private final JavaClasses applicationClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.ngockhanh.clinic");

    @Test
    void modulesRespectTheirPublishedBoundaries() {
        ApplicationModules.of(NgocKhanhClinicApplication.class).verify();
    }

    @Test
    void healthExaminationHasOnlyTheSixDocumentedAggregateRootsAndTheirRepositories() {
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().equals("com.ngockhanh.clinic.healthexamination.domain.aggregate"))
                .filter(type -> !type.getSimpleName().isBlank())
                .map(type -> type.getSimpleName())
                .toList())
                .containsExactlyInAnyOrder("Organization", "HealthExaminationParticipant", "HealthExaminationBatch",
                        "HealthExaminationBatchParticipant", "HealthExaminationRecord", "HealthExaminationImportJob");
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().equals("com.ngockhanh.clinic.healthexamination.domain.repository"))
                .filter(type -> type.getSimpleName().endsWith("Repository"))
                .map(type -> type.getSimpleName())
                .toList())
                .containsExactlyInAnyOrder("OrganizationRepository", "HealthExaminationParticipantRepository", "HealthExaminationBatchRepository",
                        "HealthExaminationBatchParticipantRepository", "HealthExaminationRecordRepository", "HealthExaminationImportJobRepository");
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
    void domainAndApplicationDoNotDependOnUuidCreator() {
        noClasses().that().resideInAnyPackage(
                        "com.ngockhanh.clinic..domain..", "com.ngockhanh.clinic..application..")
                .should().dependOnClassesThat().resideInAPackage("com.github.f4b6a3.uuid..")
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

    @Test
    void identityApplicationDoesNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic.identity.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.ngockhanh.clinic.identity.api..", "com.ngockhanh.clinic.identity.infrastructure..")
                .check(applicationClasses);
    }

    @Test
    void identityApiDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic.identity.api..")
                .should().dependOnClassesThat().resideInAPackage("com.ngockhanh.clinic.identity.infrastructure..")
                .check(applicationClasses);
    }

    @Test
    void identityUseCasePackageContainsOnlyUseCasesAndSessionRevocationIsNamedAtTypeLevel() {
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().equals("com.ngockhanh.clinic.identity.application.usecase"))
                .map(type -> type.getSimpleName())
                .filter(name -> !name.endsWith("UseCase"))
                .toList())
                .isEmpty();
        assertThat(SessionRevocation.class.getAnnotation(org.springframework.modulith.NamedInterface.class).value())
                .isEqualTo("sessions");
    }

    @Test
    void infrastructureDoesNotDependOnApiTypes() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic.identity.infrastructure..")
                .and().resideOutsideOfPackage("com.ngockhanh.clinic.identity.infrastructure.configuration")
                .should().dependOnClassesThat().resideInAPackage("com.ngockhanh.clinic.identity.api..")
                .check(applicationClasses);
    }

    @Test
    void authenticationFailuresAreRaisedInsideApplicationOrDomain() {
        assertThat(applicationClasses.stream()
                .filter(type -> type.getPackageName().startsWith("com.ngockhanh.clinic.identity.api")
                        || type.getPackageName().startsWith("com.ngockhanh.clinic.identity.infrastructure"))
                .flatMap(type -> type.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTarget().getOwner().getPackageName()
                        .equals("com.ngockhanh.clinic.identity.application.exception"))
                .filter(call -> call.getTarget().getName().equals("invalid")
                        || call.getTarget().getName().equals("invalidRequest")
                        || call.getTarget().getName().equals("rateLimited")
                        || call.getTarget().getName().equals("unavailable")))
                .isEmpty();
    }

    @Test
    void sharedDoesNotDependOnIdentity() {
        noClasses().that().resideInAPackage("com.ngockhanh.clinic.shared..")
                .should().dependOnClassesThat().resideInAPackage("com.ngockhanh.clinic.identity..")
                .check(applicationClasses);
    }
}
