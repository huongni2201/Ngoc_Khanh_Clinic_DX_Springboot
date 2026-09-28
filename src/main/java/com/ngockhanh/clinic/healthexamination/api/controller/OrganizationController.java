package com.ngockhanh.clinic.healthexamination.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ngockhanh.clinic.healthexamination.api.request.OrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeactivateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;

@Slf4j
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrganizationController {

    CreateOrganizationUseCase createOrganizationUseCase;
    GetOrganizationUseCase getOrganizationUseCase;
    UpdateOrganizationUseCase updateOrganizationUseCase;
    DeactivateOrganizationUseCase deactivateOrganizationUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationResponse>> create(
            @Valid @RequestBody OrganizationRequest request
    ) {
        log.debug("Create organization request received");
        CreateOrganizationCommand command = CreateOrganizationCommand.builder()
                .code(request.code())
                .name(request.name())
                .taxCode(request.taxCode())
                .address(request.address())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .contactJobTitle(request.contactJobTitle())
                .note(request.note())
                .build();

        OrganizationResponse response = createOrganizationUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Organization created", response));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> get(
            @PathVariable UUID organizationId
    ) {
        log.debug("Get organization request: organizationId={}", organizationId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), getOrganizationUseCase.execute(organizationId)));
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> update(
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationRequest request
    ) {
        log.debug("Update organization request: organizationId={}", organizationId);

        UpdateOrganizationCommand command = UpdateOrganizationCommand.builder()
                .code(request.code())
                .name(request.name())
                .taxCode(request.taxCode())
                .address(request.address())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .contactJobTitle(request.contactJobTitle())
                .note(request.note())
                .build();

        OrganizationResponse response = updateOrganizationUseCase.execute(organizationId, command);

        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Organization updated",
                response));
    }

    @DeleteMapping("/{organizationId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID organizationId
    ) {
        log.debug("Deactivate organization request: organizationId={}", organizationId);

        deactivateOrganizationUseCase.execute(organizationId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Organization deactivated"));
    }

}
