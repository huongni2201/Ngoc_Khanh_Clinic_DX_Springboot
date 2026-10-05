package com.ngockhanh.clinic.healthexamination.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateOrganizationRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 300) String name,
    @NotNull @Pattern(regexp = "COMPANY|SCHOOL|GOVERNMENT|OTHER") String organizationType,
    @Size(max = 50) String taxCode,
    @NotBlank String phone,
    @NotBlank @Email String email,
    @NotBlank String address,
    @NotBlank @Size(max = 200) String contactFullName,
    @Size(max = 200) String contactPosition,
    @NotBlank String contactPhone,
    @NotBlank @Email String contactEmail,
    @NotNull @PositiveOrZero Long rowVersion) {}
