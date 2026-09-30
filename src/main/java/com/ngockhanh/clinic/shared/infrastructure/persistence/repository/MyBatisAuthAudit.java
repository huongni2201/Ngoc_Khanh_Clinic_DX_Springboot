package com.ngockhanh.clinic.shared.infrastructure.persistence.repository;
import com.ngockhanh.clinic.shared.audit.AuthAudit;
import com.ngockhanh.clinic.shared.infrastructure.persistence.mapper.AuditMapper;
import com.ngockhanh.clinic.shared.infrastructure.id.IdGenerator;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;
@Repository
public class MyBatisAuthAudit implements AuthAudit {
    private final AuditMapper mapper;
    private final IdGenerator ids;
    public MyBatisAuthAudit(AuditMapper mapper, IdGenerator ids) { this.mapper = mapper; this.ids = ids; }
    public void record(UUID userId, String action, Instant at, UUID correlation) {
        mapper.insert(ids.next(), userId, action, at, correlation);
    }
}
