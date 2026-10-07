package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.OrganizationMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisOrganizationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MyBatisOrganizationRepositoryTest {
  @Test
  void maximumPageNumberUsesLongOffsetAndPreservesLiteralSearchPattern() {
    var mapper = mock(OrganizationMyBatisMapper.class);
    var repository = new MyBatisOrganizationRepository(mapper);
    long offset = ((long) Integer.MAX_VALUE - 1) * 100;
    String pattern = "%100\\%\\_\\\\%";
    when(mapper.count("ACTIVE", pattern)).thenReturn(offset + 1);
    when(mapper.search("ACTIVE", pattern, "name", "DESC", 100, offset)).thenReturn(List.of());

    var result =
        repository.search(
            Integer.MAX_VALUE, 100, "100%_\\", "name", "DESC", OrganizationStatus.ACTIVE);

    verify(mapper).search("ACTIVE", pattern, "name", "DESC", 100, offset);
    assertThat(result.items()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(offset + 1);
    assertThat(result.page()).isEqualTo(Integer.MAX_VALUE);
    assertThat(result.size()).isEqualTo(100);
    assertThat(result.totalPages()).isEqualTo(Integer.MAX_VALUE);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 11})
  void emptyPageKeepsPaginationMetadataWithoutSelectingItems(long total) {
    var mapper = mock(OrganizationMyBatisMapper.class);
    var repository = new MyBatisOrganizationRepository(mapper);
    when(mapper.count("ACTIVE", null)).thenReturn(total);

    var result = repository.search(9, 10, null, "id", "ASC", OrganizationStatus.ACTIVE);

    verify(mapper, never()).search("ACTIVE", null, "id", "ASC", 10, 80L);
    assertThat(result.items()).isEmpty();
    assertThat(result.page()).isEqualTo(9);
    assertThat(result.size()).isEqualTo(10);
    assertThat(result.totalElements()).isEqualTo(total);
    assertThat(result.totalPages()).isEqualTo(total == 0 ? 0 : 2);
  }
}
