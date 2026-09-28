package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.api.controller.StaffAuthController;
import com.ngockhanh.clinic.identity.application.port.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import com.ngockhanh.clinic.identity.application.usecase.StaffAuthentication;
import com.ngockhanh.clinic.identity.infrastructure.security.AuthConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StaffAuthController.class)
@Import(AuthConfiguration.class)
@ActiveProfiles("production")
class ProductionAuthSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    @MockitoBean StaffAuthentication authentication;

    @Test void authenticatedStaffCannotAccessBusinessEndpointsWithoutPolicy() throws Exception {
        var now = Instant.now();
        var principal = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), "staff", "STAFF",
                List.of(new StaffPrincipal.Assignment(UUID.randomUUID(), "DOCTOR", List.of("READ"),
                        null, null, now.minusSeconds(10), null)), now.plusSeconds(1800), now.plusSeconds(28800));
        when(authentication.authenticate("A".repeat(43))).thenReturn(principal);
        mvc.perform(get("/api/v1/organizations").servletPath("/api/v1/organizations")
                        .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me")
                        .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
                .andExpect(status().isOk());
    }

    @Test void csrfCookieIsSecureAndHttpOnlyInProduction() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true));
    }

    @Test void bearerTokensAreNotAcceptedAndRedisFailureIsJson503() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer arbitrary-jwt"))
                .andExpect(status().isUnauthorized());
        when(authentication.authenticate("A".repeat(43))).thenThrow(AuthenticationFailure.unavailable());
        mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me")
                        .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
    }

    @Test void successfulLoginUsesSecureHostOnlyCookieAndCreatesNoHttpSession() throws Exception {
        var now = Instant.now();
        var principal = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), "staff", "STAFF",
                List.of(), now.plusSeconds(1800), now.plusSeconds(28800));
        when(authentication.login(any())).thenReturn(new StaffAuthentication.Login("A".repeat(43), principal));
        var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        var csrf = json.readTree(csrfResponse.getContentAsString()).get("data");
        var result = mvc.perform(post("/api/v1/auth/staff/login").servletPath("/api/v1/auth/staff/login")
                        .cookie(csrfResponse.getCookie("XSRF-TOKEN"))
                        .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType("application/json").content("{\"username\":\"staff\",\"password\":\"entered-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().secure("NKC_SESSION", true))
                .andExpect(cookie().httpOnly("NKC_SESSION", true)).andExpect(cookie().path("NKC_SESSION", "/"))
                .andExpect(cookie().attribute("NKC_SESSION", "SameSite", "Lax"))
                .andExpect(cookie().doesNotExist("JSESSIONID")).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getRequest().getSession(false)).isNull();
        var session = result.getResponse().getCookie("NKC_SESSION");
        org.assertj.core.api.Assertions.assertThat(session.getDomain()).isNull();
        org.assertj.core.api.Assertions.assertThat(session.getMaxAge()).isBetween(28790, 28800);
        verify(authentication).login(argThat(command -> command.clientIp().equals("127.0.0.1")));
    }
}
