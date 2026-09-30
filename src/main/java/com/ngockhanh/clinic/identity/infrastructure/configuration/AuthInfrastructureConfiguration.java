package com.ngockhanh.clinic.identity.infrastructure.configuration;

import com.ngockhanh.clinic.identity.api.http.StaffSessionCookieFactory;
import com.ngockhanh.clinic.identity.api.http.TrustedProxyClientIpResolver;
import com.ngockhanh.clinic.identity.application.port.SessionTokens;
import com.ngockhanh.clinic.identity.domain.valueobject.StaffSessionPolicy;
import com.ngockhanh.clinic.identity.infrastructure.security.JwtSettings;
import com.ngockhanh.clinic.identity.infrastructure.security.ServerJwtTokens;
import com.ngockhanh.clinic.identity.infrastructure.session.LoginThrottleSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.function.Supplier;

@Configuration
public class AuthInfrastructureConfiguration {
    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    StaffSessionPolicy staffSessionPolicy(Environment environment) {
        return new StaffSessionPolicy(
                environment.getProperty("clinic.auth.idle-timeout", Duration.class, Duration.ofMinutes(30)),
                environment.getProperty("clinic.auth.absolute-timeout", Duration.class, Duration.ofHours(8)));
    }

    @Bean
    AuthHttpSettings authHttpSettings(Environment environment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        boolean development = profiles.contains("local") || profiles.contains("test");
        String origins = environment.getProperty("clinic.auth.allowed-origins",
                environment.getProperty("NKC_AUTH_ALLOWED_ORIGINS", development ? "http://localhost:3000" : ""));
        return new AuthHttpSettings(
                !development,
                development,
                csv(origins),
                csv(environment.getProperty("clinic.auth.trusted-proxies", "")));
    }

    @Bean
    StaffSessionCookieFactory staffSessionCookieFactory(AuthHttpSettings settings) {
        return new StaffSessionCookieFactory(settings.secureCookie());
    }

    @Bean
    TrustedProxyClientIpResolver trustedProxyClientIpResolver(AuthHttpSettings settings) {
        return new TrustedProxyClientIpResolver(settings.trustedProxies());
    }

    @Bean
    JwtSettings jwtSettings(Environment environment) {
        return new JwtSettings(environment.getProperty("clinic.auth.issuer", "nkc-clinic"),
                environment.getProperty("clinic.auth.audience", "nkc-staff"),
                environment.getProperty("clinic.auth.jwt-key", ""));
    }

    @Bean
    LoginThrottleSettings loginThrottleSettings(Environment environment) {
        return new LoginThrottleSettings(
                environment.getProperty("clinic.auth.username-limit", Integer.class, 10),
                environment.getProperty("clinic.auth.ip-limit", Integer.class, 60),
                environment.getProperty("clinic.auth.throttle-window", Duration.class, Duration.ofMinutes(15)));
    }

    @Bean
    SessionTokens sessionTokens(JwtSettings jwtSettings, StaffSessionPolicy policy, Clock clock) {
        return new ServerJwtTokens(jwtSettings, policy, clock);
    }

    @Bean
    TransactionOperations staffAccountReadTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        return transaction;
    }

    @Bean
    TransactionOperations staffAccountSnapshotTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction;
    }

    @Bean
    TransactionOperations staffAccountWriteTransaction(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    Supplier<String> sessionIds() {
        SecureRandom random = new SecureRandom();
        return () -> {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        };
    }

    private static java.util.List<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
