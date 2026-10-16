package com.ngockhanh.clinic.healthexamination.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class HealthExaminationApiSurfaceTest {
  private static final String MODULE_PACKAGE = "com.ngockhanh.clinic.healthexamination";

  private static final String AGREEMENT =
      "/api/v1/organizations/{organizationId}/agreements/{agreementId}";

  private static final String SESSIONS = "/api/v1/organizations/{organizationId}/sessions";

  private static final Set<String> ALLOWED_MAPPINGS =
      Set.of(
          "GET /api/v1/organizations",
          "GET /api/v1/organizations/{organizationId}",
          "POST /api/v1/organizations",
          "PUT /api/v1/organizations/{organizationId}",
          "DELETE /api/v1/organizations/{organizationId}",
          "GET /api/v1/organizations/{organizationId}/agreements",
          "POST /api/v1/organizations/{organizationId}/agreements",
          "GET /api/v1/organizations/{organizationId}/agreements/{agreementId}",
          "PUT /api/v1/organizations/{organizationId}/agreements/{agreementId}",
          "DELETE /api/v1/organizations/{organizationId}/agreements/{agreementId}",
          "POST " + AGREEMENT + "/activate",
          "POST " + AGREEMENT + "/end",
          "POST " + SESSIONS,
          "GET " + SESSIONS,
          "GET " + SESSIONS + "/{sessionId}",
          "PUT " + SESSIONS + "/{sessionId}",
          "POST " + SESSIONS + "/{sessionId}/cancel",
          "GET " + SESSIONS + "/{sessionId}/assignments",
          "POST " + SESSIONS + "/{sessionId}/assignments",
          "POST " + SESSIONS + "/{sessionId}/assignments/move",
          "POST " + SESSIONS + "/{sessionId}/assignments/{assignmentId}/move",
          "POST " + SESSIONS + "/{sessionId}/assignments/{assignmentId}/cancel",
          "POST " + SESSIONS + "/{sessionId}/assignments/{assignmentId}/attendance",
          "GET /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants",
          "GET /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/import-template",
          "POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/imports",
          "POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants",
          "GET /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}",
          "PUT /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}",
          "DELETE /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}",
          "GET /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}/examination-form",
          "POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}/reactivate",
          "POST /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/examination-form-print-jobs",
          "GET " + AGREEMENT + "/examination-details",
          "GET " + AGREEMENT + "/examination-details/summary",
          "GET " + AGREEMENT + "/examination-details/export",
          "GET " + AGREEMENT + "/examination-details/import-template",
          "PUT " + AGREEMENT + "/examination-details/{participantId}",
          "POST " + AGREEMENT + "/examination-details/imports",
          "GET " + AGREEMENT + "/reports/payment-summary",
          "GET " + AGREEMENT + "/reports/payment-summary/docx");

  @Test
  void healthExaminationMappingsStayWithinTheApprovedUseCases() {
    Set<String> actualMappings = mappingsOwnedByHealthExamination();

    assertThat(actualMappings).containsExactlyInAnyOrderElementsOf(ALLOWED_MAPPINGS);
  }

  private Set<String> mappingsOwnedByHealthExamination() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    var handlerMapping = new TestableRequestMappingHandlerMapping();
    var classLoader = Thread.currentThread().getContextClassLoader();

    return scanner.findCandidateComponents(MODULE_PACKAGE).stream()
        .map(candidate -> ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader))
        // Count only top-level production controllers in the published API surface.
        .filter(controllerType -> !controllerType.isMemberClass())
        .flatMap(
            controllerType ->
                java.util.Arrays.stream(controllerType.getDeclaredMethods())
                    .map(method -> mappingFor(handlerMapping, controllerType, method)))
        .filter(java.util.Objects::nonNull)
        .flatMap(this::normalizedMappings)
        .collect(Collectors.toSet());
  }

  private RequestMappingInfo mappingFor(
      TestableRequestMappingHandlerMapping handlerMapping, Class<?> controllerType, Method method) {
    return handlerMapping.mappingFor(method, controllerType);
  }

  private java.util.stream.Stream<String> normalizedMappings(RequestMappingInfo mapping) {
    Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
    if (methods.isEmpty()) {
      return mapping.getPatternValues().stream().map(path -> "* " + path);
    }
    return methods.stream()
        .flatMap(
            method -> mapping.getPatternValues().stream().map(path -> method.name() + " " + path));
  }

  private static final class TestableRequestMappingHandlerMapping
      extends RequestMappingHandlerMapping {
    private RequestMappingInfo mappingFor(Method method, Class<?> controllerType) {
      return getMappingForMethod(method, controllerType);
    }
  }
}
