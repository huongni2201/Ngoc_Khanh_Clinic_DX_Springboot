package com.ngockhanh.clinic.healthexamination.infrastructure.configuration;

import com.ngockhanh.clinic.healthexamination.infrastructure.word.ReportClinicProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the clinic identity properties used by the payment summary Word document. */
@Configuration
@EnableConfigurationProperties(ReportClinicProperties.class)
public class ReportConfiguration {}
