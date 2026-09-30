package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;

class ParticipantImportAuthorizationTest {
    @Test
    void rejectsFrontDeskBeforeRunningTheConfirmUseCase() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("front-desk", "n/a",
                            List.of(new SimpleGrantedAuthority("FRONT_DESK"))));

            assertThatThrownBy(() -> context.getBean(ConfirmParticipantImportUseCase.class)
                    .execute(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {
        @Bean
        HealthExaminationBatchRepository batches() { return mock(HealthExaminationBatchRepository.class); }

        @Bean
        HealthExaminationImportJobRepository jobs() { return mock(HealthExaminationImportJobRepository.class); }

        @Bean
        HealthExaminationParticipantRepository participants() {
            return mock(HealthExaminationParticipantRepository.class);
        }

        @Bean
        HealthExaminationBatchParticipantRepository batchParticipants() {
            return mock(HealthExaminationBatchParticipantRepository.class);
        }

        @Bean
        ParticipantImportAuditWriter auditWriter() { return mock(ParticipantImportAuditWriter.class); }

        @Bean
        IdGenerator ids() { return UUID::randomUUID; }

        @Bean
        ConfirmParticipantImportUseCase confirmParticipantImportUseCase(
                HealthExaminationBatchRepository batches,
                HealthExaminationImportJobRepository jobs,
                HealthExaminationParticipantRepository participants,
                HealthExaminationBatchParticipantRepository batchParticipants,
                ParticipantImportAuditWriter auditWriter,
                IdGenerator ids) {
            return new ConfirmParticipantImportUseCase(batches, jobs, participants, batchParticipants, auditWriter, ids);
        }
    }
}
