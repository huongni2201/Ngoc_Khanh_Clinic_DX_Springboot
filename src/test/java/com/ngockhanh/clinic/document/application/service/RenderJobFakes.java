package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.PdfMerger;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.application.rendering.DocumentRenderer;
import com.ngockhanh.clinic.document.application.rendering.RenderSession;
import com.ngockhanh.clinic.document.application.rendering.RenderedDocument;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Plain in-memory stand-ins for the ports of the print job tests. They follow the same rules as the
 * SQL (lease token checks, oldest first claiming, constraints) so the processor can be exercised
 * without a database. No framework is used, so they also run outside the build.
 */
public final class RenderJobFakes {
  private RenderJobFakes() {}

  /** A staff principal holding exactly the given permissions. */
  public static UserPrincipal staff(String... permissions) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId(UUID.randomUUID())
        .username("staff")
        .principalType("STAFF")
        .roleAssignments(
            List.of(new UserPrincipal.Assignment(UUID.randomUUID(), "CLINIC_MANAGER", List.of(permissions))))
        .build();
  }

  /** Audit events kept in a list. */
  public static final class RecordingAudit implements AuditWriter {
    public record Event(UUID actor, String action, String entityType, UUID entityId, Object after) {}

    public final List<Event> events = new ArrayList<>();

    @Override
    public void record(
        UUID actor,
        String action,
        String entityType,
        UUID entityId,
        Object beforeSnapshot,
        Object afterSnapshot) {
      events.add(new Event(actor, action, entityType, entityId, afterSnapshot));
    }

    @Override
    public void record(UUID userId, String action, Instant occurredAt, UUID correlationId) {
      events.add(new Event(userId, action, null, null, null));
    }
  }

  /** A clock the test moves by hand. */
  public static final class MutableClock extends Clock {
    private Instant now;

    public MutableClock(Instant now) {
      this.now = now;
    }

    public void advance(java.time.Duration by) {
      now = now.plus(by);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** Job table in memory. */
  public static class InMemoryStore implements RenderJobStore {
    public static final class Row {
      public UUID id;
      public String documentType;
      public UUID requestedBy;
      public UUID idempotencyKey;
      public Map<String, String> context;
      public List<UUID> recordIds;
      public String status = "QUEUED";
      public int processed;
      public List<DocumentValuesSource.Skipped> skipped = List.of();
      public String errorCode;
      public UUID templateVersionId;
      public int attempts;
      public UUID lockToken;
      public Instant lockedUntil;
      public Instant createdAt;
      public Instant finishedAt;
      public Instant expiresAt;
      public final Map<Integer, Part> parts = new TreeMap<>();
      public final Map<Integer, Integer> partCounts = new HashMap<>();
    }

    public final Map<UUID, Row> rows = new LinkedHashMap<>();
    /** Called after each progress save, to let a test interfere mid-job. */
    public Function<Row, Boolean> afterProgress = row -> true;

    public Row row(UUID id) {
      return rows.get(id);
    }

    @Override
    public Optional<Job> findById(UUID id) {
      return Optional.ofNullable(rows.get(id)).map(InMemoryStore::job);
    }

    @Override
    public Optional<Job> findByIdempotencyKey(UUID requestedBy, UUID idempotencyKey) {
      return rows.values().stream()
          .filter(r -> r.requestedBy.equals(requestedBy) && r.idempotencyKey.equals(idempotencyKey))
          .findFirst()
          .map(InMemoryStore::job);
    }

    @Override
    public int countActive(UUID requestedBy) {
      return (int)
          rows.values().stream()
              .filter(r -> r.requestedBy.equals(requestedBy))
              .filter(r -> r.status.equals("QUEUED") || r.status.equals("RUNNING"))
              .count();
    }

    @Override
    public boolean insert(
        UUID id,
        String documentType,
        UUID requestedBy,
        UUID idempotencyKey,
        Map<String, String> context,
        List<UUID> recordIds,
        Instant now) {
      if (findByIdempotencyKey(requestedBy, idempotencyKey).isPresent()) return false;
      var r = new Row();
      r.id = id;
      r.documentType = documentType;
      r.requestedBy = requestedBy;
      r.idempotencyKey = idempotencyKey;
      r.context = context;
      r.recordIds = recordIds;
      r.createdAt = now;
      rows.put(id, r);
      return true;
    }

    @Override
    public List<Part> findParts(UUID jobId) {
      return new ArrayList<>(rows.get(jobId).parts.values());
    }

    @Override
    public int failExhausted(Instant now, int maxAttempts, Instant expiresAt) {
      int failed = 0;
      for (var r : rows.values())
        if (r.status.equals("RUNNING") && r.lockedUntil.isBefore(now) && r.attempts >= maxAttempts) {
          end(r, "FAILED", "ATTEMPTS_EXHAUSTED", now, expiresAt);
          failed++;
        }
      return failed;
    }

    @Override
    public Optional<Claimed> claimNext(Instant now, Instant leaseUntil, int maxAttempts) {
      return rows.values().stream()
          .filter(
              r ->
                  r.status.equals("QUEUED")
                      || (r.status.equals("RUNNING") && r.lockedUntil.isBefore(now)))
          .filter(r -> r.attempts < maxAttempts)
          .min(java.util.Comparator.comparing((Row r) -> r.createdAt))
          .map(
              r -> {
                r.status = "RUNNING";
                r.lockToken = UUID.randomUUID();
                r.lockedUntil = leaseUntil;
                r.attempts++;
                return new Claimed(
                    r.id, r.lockToken, r.attempts, r.documentType, r.requestedBy, r.context, r.recordIds);
              });
    }

    private Row owned(UUID id, UUID token) {
      var r = rows.get(id);
      return r != null && r.status.equals("RUNNING") && token.equals(r.lockToken) ? r : null;
    }

    @Override
    public boolean recordTemplateVersion(UUID id, UUID lockToken, UUID templateVersionId) {
      var r = owned(id, lockToken);
      if (r == null) return false;
      r.templateVersionId = templateVersionId;
      return true;
    }

    @Override
    public boolean progress(
        UUID id,
        UUID lockToken,
        int processed,
        List<DocumentValuesSource.Skipped> skipped,
        Instant leaseUntil) {
      var r = owned(id, lockToken);
      if (r == null) return false;
      r.processed = processed;
      r.skipped = List.copyOf(skipped);
      r.lockedUntil = leaseUntil;
      return afterProgress.apply(r);
    }

    @Override
    public boolean addPart(
        UUID id, UUID lockToken, int partNo, int recordCount, NewFile file, Instant leaseUntil) {
      var r = owned(id, lockToken);
      if (r == null) return false;
      r.lockedUntil = leaseUntil;
      r.parts.put(
          partNo,
          new Part(
              partNo, recordCount, file.sizeBytes(), file.storageProvider(), file.storageKey(), file.checksum()));
      return true;
    }

    @Override
    public boolean finish(
        UUID id,
        UUID lockToken,
        boolean succeeded,
        String errorCode,
        int processed,
        List<DocumentValuesSource.Skipped> skipped,
        Instant now,
        Instant expiresAt) {
      var r = owned(id, lockToken);
      if (r == null) return false;
      r.processed = processed;
      r.skipped = List.copyOf(skipped);
      end(r, succeeded ? "SUCCEEDED" : "FAILED", errorCode, now, expiresAt);
      return true;
    }

    private static void end(Row r, String status, String error, Instant now, Instant expiresAt) {
      r.status = status;
      r.errorCode = error;
      r.lockToken = null;
      r.lockedUntil = null;
      r.finishedAt = now;
      r.expiresAt = expiresAt;
    }

    @Override
    public List<UUID> findExpired(Instant now, int limit) {
      return rows.values().stream()
          .filter(r -> (r.status.equals("SUCCEEDED") || r.status.equals("FAILED")))
          .filter(r -> !r.expiresAt.isAfter(now))
          .limit(limit)
          .map(r -> r.id)
          .toList();
    }

    @Override
    public void deleteParts(UUID jobId) {
      rows.get(jobId).parts.clear();
    }

    @Override
    public void markExpired(UUID jobId) {
      var r = rows.get(jobId);
      if (r.status.equals("SUCCEEDED") || r.status.equals("FAILED")) r.status = "EXPIRED";
    }

    private static Job job(Row r) {
      return new Job(
          r.id,
          r.documentType,
          r.requestedBy,
          r.idempotencyKey,
          r.context,
          r.recordIds,
          r.status,
          r.recordIds.size(),
          r.processed,
          r.skipped,
          r.errorCode,
          r.createdAt,
          r.finishedAt,
          r.expiresAt);
    }
  }

  /** Files in memory. */
  public static class InMemoryStorage implements DocumentStorage {
    public final Map<String, byte[]> files = new HashMap<>();
    public boolean writable = true;
    public boolean failWrites;
    public final List<String> deleted = new ArrayList<>();

    @Override
    public byte[] read(String provider, String key, byte[] sha256) {
      byte[] content = files.get(provider + ":" + key);
      if (content == null) throw new IllegalStateException("Stored file is missing: " + key);
      return content;
    }

    @Override
    public boolean writable(String provider) {
      return writable && "LOCAL".equals(provider);
    }

    @Override
    public byte[] write(String provider, String key, byte[] content) {
      if (!writable(provider)) throw new IllegalStateException("not writable");
      if (failWrites) throw new IllegalStateException("disk full");
      files.put(provider + ":" + key, content);
      return sha(content);
    }

    @Override
    public void delete(String provider, String key) {
      deleted.add(key);
      files.remove(provider + ":" + key);
    }

    public static byte[] sha(byte[] content) {
      try {
        return MessageDigest.getInstance("SHA-256").digest(content);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /** Merging that concatenates the bytes of the parts and counts its calls. */
  public static final class ConcatMerger implements PdfMerger {
    public final List<Integer> calls = new ArrayList<>();
    public RuntimeException failure;

    @Override
    public byte[] merge(List<byte[]> pdfs) {
      calls.add(pdfs.size());
      if (failure != null) throw failure;
      var out = new java.io.ByteArrayOutputStream();
      for (byte[] pdf : pdfs) out.writeBytes(pdf);
      return out.toByteArray();
    }
  }

  /** Renderer whose output is the record text, so the test can see what was printed. */
  public static final class FakeRenderer implements DocumentRenderer {
    public final UUID versionId = UUID.randomUUID();
    public RuntimeException openFailure;
    /** Throws for a record whose {@code id} value is in this map. */
    public final Map<String, RuntimeException> failures = new HashMap<>();
    public int opened;

    @Override
    public RenderedDocument render(String documentType, Map<String, String> values) {
      return new RenderedDocument(session().render(values), versionId, 1);
    }

    @Override
    public RenderSession open(String documentType) {
      opened++;
      if (openFailure != null) throw openFailure;
      return session();
    }

    private RenderSession session() {
      return new RenderSession() {
        @Override
        public UUID templateVersionId() {
          return versionId;
        }

        @Override
        public int templateVersionNo() {
          return 1;
        }

        @Override
        public byte[] render(Map<String, String> values) {
          var failure = failures.get(values.get("id"));
          if (failure != null) throw failure;
          return ("[" + values.get("id") + "]").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
      };
    }
  }

  /** Source that knows a fixed set of ids and skips the ones in {@code skip}. */
  public static final class FakeSource implements DocumentValuesSource {
    public final String type;
    public final Map<UUID, String> skip = new HashMap<>();
    public RuntimeException failure;
    public final List<List<UUID>> chunks = new ArrayList<>();

    public FakeSource(String type) {
      this.type = type;
    }

    @Override
    public String documentType() {
      return type;
    }

    @Override
    public Loaded load(Map<String, String> context, List<UUID> ids) {
      chunks.add(List.copyOf(ids));
      if (failure != null) throw failure;
      var items = new ArrayList<Item>();
      var skipped = new ArrayList<Skipped>();
      for (UUID id : ids)
        if (skip.containsKey(id)) skipped.add(new Skipped(id, skip.get(id)));
        else items.add(new Item(id, Map.of("id", id.toString().substring(0, 8))));
      return new Loaded(items, skipped);
    }
  }
}
