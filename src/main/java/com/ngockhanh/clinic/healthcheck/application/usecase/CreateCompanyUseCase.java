package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.application.command.CreateCompanyCommand;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateCompanyIdentity;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyRepository;
import com.ngockhanh.clinic.shared.IdGenerator;

@Service
public final class CreateCompanyUseCase {
    private final CompanyRepository companies;
    private final IdGenerator ids;

    public CreateCompanyUseCase(CompanyRepository companies, IdGenerator ids) {
        this.companies = companies;
        this.ids = ids;
    }

    @Transactional
    public UUID execute(CreateCompanyCommand command) {
        if (command == null) throw new IllegalArgumentException("Missing company command");
        if (companies.findByCode(command.code()).isPresent()
                || (command.taxCode() != null && !command.taxCode().isBlank()
                    && companies.findByTaxCode(command.taxCode()).isPresent())) {
            throw new DuplicateCompanyIdentity();
        }
        Company company = Company.create(ids.next(), command.code(), command.name(), command.taxCode(),
                command.address(), command.contactName(), command.contactPhone(), command.contactJobTitle(), command.note());
        companies.save(company);
        return company.id();
    }
}