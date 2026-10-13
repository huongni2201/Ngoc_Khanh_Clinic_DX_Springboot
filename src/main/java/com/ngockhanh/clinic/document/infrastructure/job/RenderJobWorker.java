package com.ngockhanh.clinic.document.infrastructure.job;

import com.ngockhanh.clinic.document.application.service.RenderJobCleanupService;
import com.ngockhanh.clinic.document.application.service.RenderJobProcessor;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for print jobs and runs them on a thread of its own, so a job that takes minutes never
 * blocks other scheduled work. One job runs at a time per application instance; several instances
 * share the queue through the database lease. Switch off with {@code nkc.document.jobs.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "nkc.document.jobs.enabled", havingValue = "true", matchIfMissing = true)
class RenderJobWorker {
  private static final Logger log = LoggerFactory.getLogger(RenderJobWorker.class);

  private final RenderJobProcessor processor;
  private final RenderJobCleanupService cleanup;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "render-job-worker");
            thread.setDaemon(true);
            return thread;
          });

  RenderJobWorker(RenderJobProcessor processor, RenderJobCleanupService cleanup) {
    this.processor = processor;
    this.cleanup = cleanup;
  }

  @Scheduled(
      initialDelayString = "${nkc.document.jobs.poll-interval:PT5S}",
      fixedDelayString = "${nkc.document.jobs.poll-interval:PT5S}")
  void poll() {
    if (!running.compareAndSet(false, true)) return;
    executor.execute(
        () -> {
          try {
            while (processor.processNext()) {
              // keep going until the queue is empty
            }
          } catch (RuntimeException e) {
            log.error("Render job polling failed: type={}", e.getClass().getSimpleName());
          } finally {
            running.set(false);
          }
        });
  }

  @Scheduled(
      initialDelayString = "${nkc.document.jobs.cleanup-interval:PT10M}",
      fixedDelayString = "${nkc.document.jobs.cleanup-interval:PT10M}")
  void expire() {
    try {
      cleanup.cleanUp();
    } catch (RuntimeException e) {
      log.error("Render job cleanup failed: type={}", e.getClass().getSimpleName());
    }
  }

  @PreDestroy
  void stop() {
    executor.shutdownNow();
  }
}
