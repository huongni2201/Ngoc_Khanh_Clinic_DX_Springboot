package com.ngockhanh.clinic.document.infrastructure.configuration;

import com.ngockhanh.clinic.document.application.service.StarterTemplateSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the starter templates once the application has started and migrations have run. Turn it off
 * with {@code nkc.document.seed-starter-templates=false}.
 */
@Component
@ConditionalOnProperty(
    name = "nkc.document.seed-starter-templates",
    havingValue = "true",
    matchIfMissing = true)
class StarterTemplateSeedRunner implements ApplicationRunner {
  private final StarterTemplateSeeder seeder;

  StarterTemplateSeedRunner(StarterTemplateSeeder seeder) {
    this.seeder = seeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    seeder.seed();
  }
}
