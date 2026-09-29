package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record OrganizationRequest(
        @NotBlank @Size(max = 300) String name,
        @Size(max = 40) String taxCode,
        @Size(max = 500) String address,
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Size(max = 30) String contactPhone,
        @Size(max = 150) String contactJobTitle,
        @Size(max = 1000) String note) {
}
