package com.ngockhanh.clinic.healthexamination.application.response;

import java.util.UUID;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import lombok.Builder;

@Builder
public record OrganizationResponse(
		UUID id,
		String name,
		String taxCode,
		String address,
		String contactName,
		String contactPhone,
		String contactJobTitle,
		String note,
		String status) {

	public static OrganizationResponse from(Organization organization) {
		return new OrganizationResponse(
				organization.id().value(),
				organization.name(),
				organization.taxCode(),
				organization.address(),
				organization.contactName(),
				organization.contactPhone(),
				organization.contactJobTitle(),
				organization.note(),
				organization.status());
	}
}
