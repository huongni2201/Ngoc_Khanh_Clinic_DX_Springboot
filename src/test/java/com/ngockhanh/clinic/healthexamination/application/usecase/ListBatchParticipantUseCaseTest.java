package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationBatchParticipantResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.web.PageResponse;

class ListBatchParticipantUseCaseTest {
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final HealthExaminationBatchRepository batchQuery = mock(HealthExaminationBatchRepository.class);
    private final HealthExaminationBatchParticipantRepository participantQuery = mock(HealthExaminationBatchParticipantRepository.class);
    private final ListBatchParticipantUseCase useCase = new ListBatchParticipantUseCase(batchQuery, participantQuery);

    @BeforeEach
    void batchExists() {
        when(batchQuery.findById(AggregateId.of(BATCH_ID))).thenReturn(Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID), LocalDate.of(2026, 9, 28))));
    }

    @Test
    void appliesDefaultsAndBuildsPageFromQueryResult() {
        when(participantQuery.countByBatch(AggregateId.of(BATCH_ID), null)).thenReturn(21L);
        when(participantQuery.findByBatch(AggregateId.of(BATCH_ID), 0, 10, null, "id", "ASC"))
                .thenReturn(List.of());

        PageResponse<OrganizationBatchParticipantResponse> response = useCase.execute(
                ORGANIZATION_ID, BATCH_ID, OrganizationBatchParticipantQuery.builder().build());

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(3);
        verify(participantQuery).findByBatch(AggregateId.of(BATCH_ID), 0, 10, null, "id", "ASC");
    }

    @Test
    void normalizesSearchAndSortBeforeQuerying() {
        String searchPattern = "%a\\%\\_\\\\b%";
        when(participantQuery.countByBatch(AggregateId.of(BATCH_ID), searchPattern)).thenReturn(1L);
        when(participantQuery.findByBatch(AggregateId.of(BATCH_ID), 20, 20, searchPattern, "fullName", "DESC"))
                .thenReturn(List.of());

        useCase.execute(ORGANIZATION_ID, BATCH_ID, OrganizationBatchParticipantQuery.builder()
                .page(2)
                .size(20)
                .searchKey(" A%_\\B ")
                .sortKey(" fullName ")
                .sortBy(" desc ")
                .build());

        verify(participantQuery).findByBatch(AggregateId.of(BATCH_ID), 20, 20, searchPattern, "fullName", "DESC");
    }

    @Test
    void rejectsInvalidPaginationAndSortValues() {
        assertThatThrownBy(() -> execute(OrganizationBatchParticipantQuery.builder().page(0).size(10).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid pagination");
        assertThatThrownBy(() -> execute(OrganizationBatchParticipantQuery.builder().page(1).size(101).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid pagination");
        assertThatThrownBy(() -> execute(OrganizationBatchParticipantQuery.builder().page(1).size(10).sortKey("unknown").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid sort key");
        assertThatThrownBy(() -> execute(OrganizationBatchParticipantQuery.builder().page(1).size(10).sortBy("sideways").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid sort direction");
    }

    private void execute(OrganizationBatchParticipantQuery query) {
        useCase.execute(ORGANIZATION_ID, BATCH_ID, query);
    }
}
