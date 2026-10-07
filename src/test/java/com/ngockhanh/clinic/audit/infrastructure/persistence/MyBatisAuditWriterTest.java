package com.ngockhanh.clinic.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.infrastructure.persistence.mapper.AuditEventMapper;
import com.ngockhanh.clinic.audit.infrastructure.persistence.record.AuditEventRecord;
import com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class MyBatisAuditWriterTest {
  private final AuditEventMapper mapper = mock(AuditEventMapper.class);
  private final JsonMapper json = JsonMapper.builder().build();
  private final MyBatisAuditWriter writer = new MyBatisAuditWriter(mapper, json);
  private final UUID actor = UUID.randomUUID();

  @Test
  void serializesBusinessSnapshotsAndLeavesTimeToTheDatabase() {
    when(mapper.insert(any())).thenReturn(1);
    UUID resource = UUID.randomUUID();
    writer.record(
        actor, "CREATE_ORGANIZATION", "ORGANIZATION", resource, null, Map.of("taxCode", "TAX-01"));

    var event = ArgumentCaptor.forClass(AuditEventRecord.class);
    verify(mapper).insert(event.capture());
    var recorded = event.getValue();
    assertThat(recorded.actorAccountId()).isEqualTo(actor);
    assertThat(recorded.action()).isEqualTo("CREATE_ORGANIZATION");
    assertThat(recorded.resourceType()).isEqualTo("ORGANIZATION");
    assertThat(recorded.resourceId()).isEqualTo(resource);
    assertThat(recorded.occurredAt()).isNull();
    assertThat(recorded.correlationId()).isNull();
    assertThat(recorded.departmentId()).isNull();
    assertThat(recorded.id().version()).isEqualTo(7);
    var metadata = json.readTree(recorded.metadata());
    assertThat(metadata.get("before").isEmpty()).isTrue();
    assertThat(metadata.get("after").get("taxCode").asString()).isEqualTo("TAX-01");
    assertThat(metadata.get("after").size()).isEqualTo(1);
  }

  @Test
  void preservesAuthenticationTimeAndCorrelation() {
    when(mapper.insert(any())).thenReturn(1);
    var occurredAt = Instant.parse("2026-10-07T01:00:00Z");
    var correlation = UUID.randomUUID();
    writer.record(actor, "ACCOUNT_LOGIN", occurredAt, correlation);
    var event = ArgumentCaptor.forClass(AuditEventRecord.class);
    verify(mapper).insert(event.capture());
    assertThat(event.getValue().occurredAt()).isEqualTo(occurredAt);
    assertThat(event.getValue().correlationId()).isEqualTo(correlation);
    assertThat(event.getValue().resourceId()).isEqualTo(actor);
    assertThat(event.getValue().resourceType()).isEqualTo("ACCOUNT");
  }

  @Test
  void rejectsAnUnwrittenAuditEvent() {
    when(mapper.insert(any())).thenReturn(0);
    assertThatThrownBy(
            () ->
                writer.record(
                    actor, "CREATE_ORGANIZATION", "ORGANIZATION", UUID.randomUUID(), null, null))
        .isInstanceOf(IllegalStateException.class);
  }
}
