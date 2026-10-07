package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.ParticipantFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantListReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListQuery;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantPage;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListParticipantsUseCaseTest {
  private final ParticipantListReader reader = mock(ParticipantListReader.class);
  private final ListParticipantsUseCase useCase =
      new ListParticipantsUseCase(new ParticipantAccessPolicy(), reader);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();

  private ParticipantListCriteria readWith(ParticipantListQuery query) {
    when(reader.readPage(eq(organizationId), eq(batchId), any()))
        .thenReturn(Optional.of(new ParticipantPage(List.of(), 0)));
    useCase.execute(organizationId, batchId, query, staff(READ));
    var criteria = ArgumentCaptor.forClass(ParticipantListCriteria.class);
    verify(reader).readPage(eq(organizationId), eq(batchId), criteria.capture());
    return criteria.getValue();
  }

  @Test
  void deniesCallersWithoutTheReadPermissionBeforeTouchingTheReader() {
    var query = ParticipantListQuery.builder().build();
    for (var principal : List.of(staff(), staff(IMPORT), patient()))
      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, query, principal))
          .isInstanceOfSatisfying(
              ApplicationException.class,
              denied ->
                  assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(() -> useCase.execute(organizationId, batchId, query, null))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(reader);
  }

  @Test
  void appliesTheDocumentedDefaults() {
    var criteria = readWith(ParticipantListQuery.builder().build());

    assertThat(criteria.offset()).isZero();
    assertThat(criteria.limit()).isEqualTo(10);
    assertThat(criteria.sortKey()).isEqualTo("id");
    assertThat(criteria.sortBy()).isEqualTo("ASC");
    assertThat(criteria.searchPattern()).isNull();
    assertThat(criteria.rosterStatus()).isNull();
  }

  @Test
  void passesFiltersAndComputesTheOffsetFromPageAndSize() {
    var day = UUID.randomUUID();
    var criteria =
        readWith(
            ParticipantListQuery.builder()
                .page(3)
                .size(25)
                .identificationNumber("012345678901")
                .batchDayId(day)
                .rosterStatus("CANCELLED")
                .attendanceStatus("ATTENDED")
                .reconciliationStatus("RECONCILED")
                .sortKey("fullName")
                .sortBy("desc")
                .build());

    assertThat(criteria.offset()).isEqualTo(50);
    assertThat(criteria.limit()).isEqualTo(25);
    assertThat(criteria.identificationNumber()).isEqualTo("012345678901");
    assertThat(criteria.batchDayId()).isEqualTo(day);
    assertThat(criteria.rosterStatus()).isEqualTo("CANCELLED");
    assertThat(criteria.attendanceStatus()).isEqualTo("ATTENDED");
    assertThat(criteria.reconciliationStatus()).isEqualTo("RECONCILED");
    assertThat(criteria.sortKey()).isEqualTo("fullName");
    assertThat(criteria.sortBy()).isEqualTo("DESC");
  }

  @Test
  void treatsLikeWildcardsInTheSearchKeyAsLiteralText() {
    var criteria = readWith(ParticipantListQuery.builder().searchKey("  50%_Off\\ ").build());

    assertThat(criteria.searchPattern()).isEqualTo("%50\\%\\_off\\\\%");
  }

  @Test
  void rejectsValuesOutsideTheAllowlistsInsteadOfIgnoringThem() {
    for (var query :
        List.of(
            ParticipantListQuery.builder().page(0).build(),
            ParticipantListQuery.builder().size(0).build(),
            ParticipantListQuery.builder().size(101).build(),
            ParticipantListQuery.builder().sortKey("password").build(),
            ParticipantListQuery.builder().sortBy("sideways").build(),
            ParticipantListQuery.builder().rosterStatus("DELETED").build(),
            ParticipantListQuery.builder().attendanceStatus("LATE").build(),
            ParticipantListQuery.builder().reconciliationStatus("DONE").build(),
            ParticipantListQuery.builder().identificationNumber("12 34").build(),
            ParticipantListQuery.builder().searchKey("x".repeat(201)).build()))
      assertThatThrownBy(() -> useCase.execute(organizationId, batchId, query, staff(READ)))
          .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(reader);
  }

  @Test
  void reportsABatchOutsideTheOrganizationOrDeletedAsNotFound() {
    when(reader.readPage(eq(organizationId), eq(batchId), any())).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    organizationId, batchId, ParticipantListQuery.builder().build(), staff(READ)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void masksTheIdentificationNumberAndKeepsTheTotals() {
    when(reader.readPage(eq(organizationId), eq(batchId), any()))
        .thenReturn(
            Optional.of(new ParticipantPage(List.of(summary("012345678901"), summary("123")), 21)));

    var page =
        useCase.execute(
            organizationId,
            batchId,
            ParticipantListQuery.builder().page(1).size(10).build(),
            staff(READ));

    assertThat(page.items())
        .extracting(item -> item.identificationNumberMasked())
        .containsExactly("********8901", "***");
    assertThat(page.totalElements()).isEqualTo(21);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(10);
  }

  @Test
  void aPagePastTheEndKeepsTheTotalWithNoItemsAndAnEmptyResultHasZeroPages() {
    when(reader.readPage(eq(organizationId), eq(batchId), any()))
        .thenReturn(Optional.of(new ParticipantPage(List.of(), 5)));
    var past =
        useCase.execute(
            organizationId, batchId, ParticipantListQuery.builder().page(9).build(), staff(READ));
    assertThat(past.items()).isEmpty();
    assertThat(past.totalElements()).isEqualTo(5);
    assertThat(past.totalPages()).isEqualTo(1);

    when(reader.readPage(eq(organizationId), eq(batchId), any()))
        .thenReturn(Optional.of(new ParticipantPage(List.of(), 0)));
    var empty =
        useCase.execute(
            organizationId, batchId, ParticipantListQuery.builder().build(), staff(READ));
    assertThat(empty.totalPages()).isZero();
  }
}
