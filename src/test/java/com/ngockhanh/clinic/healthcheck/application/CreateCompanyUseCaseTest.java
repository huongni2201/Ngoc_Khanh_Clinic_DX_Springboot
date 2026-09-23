package com.ngockhanh.clinic.healthcheck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthcheck.application.command.CreateCompanyCommand;
import com.ngockhanh.clinic.healthcheck.application.usecase.CreateCompanyUseCase;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateCompanyIdentity;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyRepository;

class CreateCompanyUseCaseTest {
    @Test
    void createsCompanyWithApplicationGeneratedIdentityAndAllDocumentedFields() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        InMemoryCompanies companies = new InMemoryCompanies();
        CreateCompanyUseCase useCase = new CreateCompanyUseCase(companies, () -> id);

        UUID result = useCase.execute(new CreateCompanyCommand("C01", "Clinic Corp", "TAX-1", "Address",
                "Contact", "0900000000", "Director", "Note"));

        assertThat(result).isEqualTo(id);
        Company saved = companies.findById(id).orElseThrow();
        assertThat(saved.code()).isEqualTo("C01");
        assertThat(saved.name()).isEqualTo("Clinic Corp");
        assertThat(saved.taxCode()).isEqualTo("TAX-1");
        assertThat(saved.address()).isEqualTo("Address");
        assertThat(saved.contactJobTitle()).isEqualTo("Director");
        assertThat(saved.note()).isEqualTo("Note");
        assertThat(saved.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateCompanyCodeBeforeInsert() {
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InMemoryCompanies companies = new InMemoryCompanies();
        companies.save(Company.create(existingId, "C01", "Clinic Corp", "Contact", "0900000000"));
        CreateCompanyUseCase useCase = new CreateCompanyUseCase(companies, UUID::randomUUID);

        assertThatThrownBy(() -> useCase.execute(new CreateCompanyCommand("C01", "Other Corp", null, null,
                "Contact", "0900000000", null, null))).isInstanceOf(DuplicateCompanyIdentity.class);
    }

    private static final class InMemoryCompanies implements CompanyRepository {
        private final Map<UUID, Company> companies = new HashMap<>();

        @Override
        public Optional<Company> findById(UUID id) { return Optional.ofNullable(companies.get(id)); }
        @Override
        public Optional<Company> findByCode(String code) {
            return companies.values().stream().filter(company -> company.code().equals(code)).findFirst();
        }
        @Override
        public Optional<Company> findByTaxCode(String taxCode) {
            return companies.values().stream().filter(company -> taxCode.equals(company.taxCode())).findFirst();
        }
        @Override
        public void save(Company company) { companies.put(company.id(), company); }
    }
}