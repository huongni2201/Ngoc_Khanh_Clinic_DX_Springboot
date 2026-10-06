package com.ngockhanh.clinic.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.NgocKhanhClinicApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleVerificationTest {
  private final JavaClasses applicationClasses =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.ngockhanh.clinic");

  @Test
  void modulesRespectTheirPublishedBoundaries() {
    productionModules().verify();
  }

  @Test
  void modulesMatchTheCleanSlateBoundedContextsAndSharedTechnicalCode() {
    assertThat(productionModules())
        .extracting(module -> module.getIdentifier().toString())
        .containsExactlyInAnyOrder(
            "accesscontrol",
            "patient",
            "catalog",
            "encounter",
            "clinical",
            "billing",
            "diagnostics",
            "healthexamination",
            "document",
            "prescription",
            "notification",
            "integration",
            "appointment",
            "portal",
            "audit",
            "shared");
  }

  @Test
  void auditAndAppointmentTypesBelongToTheirOwningModules() {
    assertThat(
            applicationClasses.stream()
                .filter(
                    type ->
                        type.getSimpleName().equals("AuditWriter")
                            || type.getSimpleName().equals("AuditEventMapper")
                            || type.getSimpleName().equals("AuditEventRecord")
                            || type.getSimpleName().equals("MyBatisAuditWriter"))
                .map(JavaClass::getPackageName)
                .toList())
        .hasSize(4)
        .allMatch(name -> name.startsWith("com.ngockhanh.clinic.audit."));
    assertThat(
            applicationClasses.stream()
                .filter(type -> type.getSimpleName().equals("AppointmentRecord"))
                .map(JavaClass::getPackageName)
                .toList())
        .containsExactly("com.ngockhanh.clinic.appointment.infrastructure.persistence.record");
  }

  private ApplicationModules productionModules() {
    return ApplicationModules.of(
        NgocKhanhClinicApplication.class,
        DescribedPredicate.describe(
            "classes outside the production import",
            type -> !applicationClasses.contain(type.getName())));
  }

  @Test
  void healthExaminationHasOnlyTheCleanSlateAggregateRootsAndTheirRepositories() {
    assertThat(
            applicationClasses.stream()
                .filter(
                    type ->
                        type.getPackageName()
                            .equals("com.ngockhanh.clinic.healthexamination.domain.aggregate"))
                .filter(type -> !type.getName().contains("$"))
                .filter(type -> !type.getSimpleName().isBlank())
                .map(JavaClass::getSimpleName)
                .toList())
        .containsExactlyInAnyOrder(
            "Organization",
            "HealthExaminationBatch",
            "HealthExaminationBatchParticipant",
            "HealthExaminationRecord");
    assertThat(
            applicationClasses.stream()
                .filter(
                    type ->
                        type.getPackageName()
                            .equals("com.ngockhanh.clinic.healthexamination.domain.repository"))
                .filter(type -> type.getSimpleName().endsWith("Repository"))
                .map(type -> type.getSimpleName())
                .toList())
        .containsExactlyInAnyOrder(
            "OrganizationRepository",
            "HealthExaminationBatchRepository",
            "HealthExaminationBatchParticipantRepository",
            "HealthExaminationRecordRepository");
  }

  @Test
  void domainDoesNotDependOnFrameworkPersistenceOrTransportTypes() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.apache.ibatis..",
            "com.ngockhanh.clinic..infrastructure..",
            "com.ngockhanh.clinic..api.request..",
            "com.ngockhanh.clinic..api.response..")
        .check(applicationClasses);
  }

  @Test
  void domainAndApplicationDoNotDependOnUuidCreator() {
    noClasses()
        .that()
        .resideInAnyPackage("com.ngockhanh.clinic..domain..", "com.ngockhanh.clinic..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.github.f4b6a3.uuid..")
        .check(applicationClasses);
  }

  @Test
  void useCasesKeepPersistencePayloadSerializationInAdapters() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic..application.usecase..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("tools.jackson..")
        .check(applicationClasses);
  }

  @Test
  void healthExaminationApplicationDoesNotDependOnItsInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic.healthexamination.application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ngockhanh.clinic.healthexamination.infrastructure..")
        .check(applicationClasses);
  }

  @Test
  void apiAndApplicationDoNotDependOnMyBatisOrPersistenceRecords() {
    noClasses()
        .that()
        .resideInAnyPackage("com.ngockhanh.clinic..api..", "com.ngockhanh.clinic..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.apache.ibatis..",
            "com.ngockhanh.clinic..infrastructure.persistence.mapper..",
            "com.ngockhanh.clinic..infrastructure.persistence.record..")
        .check(applicationClasses);
  }

  @Test
  void apiDoesNotCallDomainOrInfrastructureDirectly() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.ngockhanh.clinic..domain..", "com.ngockhanh.clinic..infrastructure..")
        .allowEmptyShould(true)
        .check(applicationClasses);
  }

  @Test
  void accessControlApplicationDoesNotDependOnApiOrInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.ngockhanh.clinic.accesscontrol.api..",
            "com.ngockhanh.clinic.accesscontrol.infrastructure..")
        .allowEmptyShould(true)
        .check(applicationClasses);
  }

  @Test
  void accessControlApiDoesNotDependOnInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol.api..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol.infrastructure..")
        .allowEmptyShould(true)
        .check(applicationClasses);
  }

  @Test
  void accessControlInfrastructureDoesNotDependOnApiTypes() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol.infrastructure..")
        .and()
        .resideOutsideOfPackage("com.ngockhanh.clinic.accesscontrol.infrastructure.configuration")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol.api..")
        .allowEmptyShould(true)
        .check(applicationClasses);
  }

  @Test
  void sharedDoesNotDependOnAccessControl() {
    noClasses()
        .that()
        .resideInAPackage("com.ngockhanh.clinic.shared..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ngockhanh.clinic.accesscontrol..")
        .check(applicationClasses);
  }
}
