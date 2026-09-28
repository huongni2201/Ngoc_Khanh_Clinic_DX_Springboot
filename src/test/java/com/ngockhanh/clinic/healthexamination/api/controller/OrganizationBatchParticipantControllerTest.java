package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ngockhanh.clinic.healthexamination.api.request.OrganizationBatchParticipantRequest;
import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationBatchParticipantResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListBatchParticipantUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;

class OrganizationBatchParticipantControllerTest {

    private ListBatchParticipantUseCase listBatchParticipantUseCase;
    private OrganizationBatchParticipantController controller;

    @BeforeEach
    void setUp() {
        listBatchParticipantUseCase = mock(ListBatchParticipantUseCase.class);
        controller = new OrganizationBatchParticipantController(listBatchParticipantUseCase);
    }

    @Test
    void participantsBuildsQueryAndReturnsPagedResponse() {
        UUID organizationId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        OrganizationBatchParticipantRequest request = OrganizationBatchParticipantRequest.builder()
                .page(2)
                .size(20)
                .searchKey("Nguyen")
                .sortBy("ASC")
                .sortKey("fullName")
                .build();

        OrganizationBatchParticipantResponse item = OrganizationBatchParticipantResponse.builder()
                .batchParticipantId(UUID.randomUUID())
                .fullName("Nguyen Van A")
                .build();

        PageResponse<OrganizationBatchParticipantResponse> pageResponse =
                new PageResponse<>(List.of(item), 2, 20, 1, 1);

        when(listBatchParticipantUseCase.execute(eq(organizationId), eq(batchId), any(OrganizationBatchParticipantQuery.class)))
                .thenReturn(pageResponse);

        ResponseEntity<ApiResponse<PageResponse<OrganizationBatchParticipantResponse>>> responseEntity =
                controller.participants(organizationId, batchId, request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().code()).isEqualTo(HttpStatus.OK.value());
        assertThat(responseEntity.getBody().message()).isEqualTo("Health examination batch participants");
        assertThat(responseEntity.getBody().data()).isEqualTo(pageResponse);

        ArgumentCaptor<OrganizationBatchParticipantQuery> captor =
                ArgumentCaptor.forClass(OrganizationBatchParticipantQuery.class);
        verify(listBatchParticipantUseCase).execute(eq(organizationId), eq(batchId), captor.capture());

        OrganizationBatchParticipantQuery captured = captor.getValue();
        assertThat(captured.page()).isEqualTo(2);
        assertThat(captured.size()).isEqualTo(20);
        assertThat(captured.searchKey()).isEqualTo("Nguyen");
        assertThat(captured.sortBy()).isEqualTo("ASC");
        assertThat(captured.sortKey()).isEqualTo("fullName");
    }
}
