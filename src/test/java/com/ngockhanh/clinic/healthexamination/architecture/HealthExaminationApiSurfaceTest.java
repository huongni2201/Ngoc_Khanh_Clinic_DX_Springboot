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

  private static final Set<String> ALLOWED_MAPPINGS =
      Set.of(
          "GET /api/v1/organizations/{organizationId}",
          "POST /api/v1/organizations",
          "PUT /api/v1/organizations/{organizationId}",
          "GET /api/v1/organizations/{organizationId}/health-examination-batches",
          "POST /api/v1/organizations/{organizationId}/health-examination-batches");

  private static final Set<String> REMOVED_MAPPINGS =
      Set.of(
          "GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/export-template",
          "POST /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports",
          "GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}",
          "GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}/rows",
          "PUT /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}/preview",
          "POST /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}/confirm",
          "POST /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}/cancel",
          "GET /api/v1/organizations",
          "DELETE /api/v1/organizations/{organizationId}",
          "GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}",
          "PUT /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}",
          "GET /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants");

  @Test
  void healthExaminationMappingsStayWithinTheApprovedUseCases() {
    Set<String> actualMappings = mappingsOwnedByHealthExamination();

    assertThat(actualMappings).containsExactlyInAnyOrderElementsOf(ALLOWED_MAPPINGS);
    assertThat(actualMappings).doesNotContainAnyElementsOf(REMOVED_MAPPINGS);
  }

  private Set<String> mappingsOwnedByHealthExamination() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    var handlerMapping = new TestableRequestMappingHandlerMapping();
    var classLoader = Thread.currentThread().getContextClassLoader();

    return scanner.findCandidateComponents(MODULE_PACKAGE).stream()
        .map(candidate -> ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader))
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
