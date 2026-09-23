package com.ngockhanh.clinic.healthcheck.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyEmployeeRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MyBatisCompanyRepositoryIntegrationTest {
    private static java.util.UUID id(long suffix) {
        return java.util.UUID.fromString("01990000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    @Container
    static final MSSQLServerContainer<?> SQL_SERVER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL_SERVER::getJdbcUrl);
        registry.add("spring.datasource.username", SQL_SERVER::getUsername);
        registry.add("spring.datasource.password", SQL_SERVER::getPassword);
    }

    @Autowired
    CompanyRepository companies;

    @Autowired
    CompanyEmployeeRepository employees;

    @Test
    void savesAndRestoresCompanyThroughMyBatisAgainstSqlServer() {
        Company expected = Company.create(id(1), "MYBATIS-C01", "Company", "TAX-01",
                "Address", "Contact", "0900000000", "Director", "Note");

        companies.save(expected);

        Company restored = companies.findById(expected.id()).orElseThrow();
        assertThat(restored.code()).isEqualTo(expected.code());
        assertThat(restored.name()).isEqualTo(expected.name());
        assertThat(restored.taxCode()).isEqualTo(expected.taxCode());
        assertThat(restored.address()).isEqualTo(expected.address());
        assertThat(companies.findByCode(expected.code())).get().extracting(Company::id).isEqualTo(expected.id());
        assertThat(companies.findByTaxCode(expected.taxCode())).get().extracting(Company::id).isEqualTo(expected.id());
    }
    @Test
    void savesReimportsAndFindsEmployeeByCompanyRosterIdentity() {
        java.util.UUID companyId = id(2);
        java.util.UUID employeeId = id(3);
        companies.save(Company.create(companyId, "MYBATIS-EMP-CO", "Company", "Contact", "0900000000"));
        CompanyEmployee employee = CompanyEmployee.create(employeeId, companyId, "E01",
                IdentificationNumber.of("987654321098"), "Nguyen A", java.time.LocalDate.of(1990, 1, 1), "MALE",
                "Department", "Technician", "Technician");
        employees.save(employee);

        CompanyEmployee restored = employees.findById(employeeId).orElseThrow();
        assertThat(restored.departmentName()).isEqualTo("Department");
        assertThat(employees.findByCompanyAndCode(companyId, "E01")).get().extracting(CompanyEmployee::id).isEqualTo(employeeId);
        assertThat(employees.findByCompanyAndIdentificationNumber(companyId, employee.identificationNumber()))
                .get().extracting(CompanyEmployee::id).isEqualTo(employeeId);

        employees.save(restored.reimport("E01", employee.identificationNumber(), "Updated Name", employee.dateOfBirth(),
                employee.sex(), "New Department", employee.jobTitle(), employee.occupation()));

        assertThat(employees.findById(employeeId).orElseThrow().fullName()).isEqualTo("Updated Name");
    }
}
