package com.ngockhanh.clinic.accesscontrol.infrastructure.configuration;

import com.ngockhanh.clinic.accesscontrol.application.port.EndpointPermissionCatalog;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.EndpointPermissionInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers the per-endpoint permission check for business routes (ADR-0015). Endpoint permissions
 * are read from the database once at startup; changing them requires a restart.
 */
@Slf4j
@Configuration
public class AccessControlWebConfiguration {

  @Bean
  EndpointPermissionInterceptor endpointPermissionInterceptor(EndpointPermissionCatalog catalog) {
    var endpointPermissions = catalog.findAll();
    if (endpointPermissions.isEmpty())
      log.warn("No endpoint permissions are stored; every business endpoint is denied");
    else log.info("Endpoint permissions loaded: count={}", endpointPermissions.size());
    return new EndpointPermissionInterceptor(endpointPermissions);
  }

  @Bean
  WebMvcConfigurer endpointPermissionRegistration(EndpointPermissionInterceptor interceptor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(interceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/api/v1/auth/**");
      }
    };
  }

  /** Warns about controller endpoints and stored permissions that do not match each other. */
  @EventListener(ApplicationReadyEvent.class)
  void reportEndpointPermissionCoverage(ApplicationReadyEvent event) {
    var context = event.getApplicationContext();
    if (!context.containsBean("requestMappingHandlerMapping")) return;
    var mappings =
        context
            .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
            .getHandlerMethods()
            .keySet();
    var interceptor = context.getBean(EndpointPermissionInterceptor.class);
    interceptor
        .endpointsWithoutPermission(mappings)
        .forEach(endpoint -> log.warn("Endpoint has no stored permission and is denied: {}", endpoint));
    interceptor
        .permissionsWithoutEndpoint(mappings)
        .forEach(endpoint -> log.warn("Stored permission names no controller endpoint: {}", endpoint));
  }
}
