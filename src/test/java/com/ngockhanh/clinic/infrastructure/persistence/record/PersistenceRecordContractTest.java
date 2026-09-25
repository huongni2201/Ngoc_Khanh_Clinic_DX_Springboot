package com.ngockhanh.clinic.infrastructure.persistence.record;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRecordContractTest {

    @org.junit.jupiter.api.Test
    void resolvedServiceRequestIdentifierUsesUuidLikeItsSqlServerColumn() throws Exception {
        RecordComponent component = Arrays.stream(Class.forName(
                "com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationImportRowRecord")
                .getRecordComponents())
                .filter(candidate -> candidate.getName().equals("resolvedServiceRequestId"))
                .findFirst()
                .orElseThrow();

        assertThat(component.getType()).isEqualTo(UUID.class);
    }

    @org.junit.jupiter.api.Test
    void assignmentBillableRecordComponentIsPrimitiveLikeNotNullSqlColumn() throws Exception {
        RecordComponent component = Arrays.stream(Class.forName(
                "com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchEmployeeServiceRecord")
                .getRecordComponents())
                .filter(candidate -> candidate.getName().equals("billable"))
                .findFirst()
                .orElseThrow();

        assertThat(component.getType()).isEqualTo(boolean.class);
    }
    @ParameterizedTest
    @MethodSource("latestSchemaRecords")
    void recordComponentsFollowLatestSchemaColumnOrder(String className, List<String> componentNames)
            throws ClassNotFoundException {
        Class<?> recordType = Class.forName(className);

        assertThat(recordType.isRecord()).isTrue();
        assertThat(Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .map(PersistenceRecordContractTest::toSnakeCase)
                .toList())
                .containsExactlyElementsOf(componentNames);
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static Stream<Arguments> latestSchemaRecords() {
        return Stream.of(
                Arguments.of("com.ngockhanh.clinic.patient.infrastructure.persistence.record.PatientRecord", List.of("id", "patient_code", "identification_number", "full_name", "full_name_normalized", "date_of_birth", "sex", "phone", "email", "address", "ward_code", "province_code", "occupation", "note", "status", "created_at", "updated_at", "row_version")),
                Arguments.of("com.ngockhanh.clinic.patient.infrastructure.persistence.record.PatientAllergyRecord", List.of("id", "patient_id", "substance_code", "substance_name", "reaction", "severity", "verification_status", "recorded_by_user_id", "recorded_at", "ended_at")),
                Arguments.of("com.ngockhanh.clinic.patient.infrastructure.persistence.record.PatientConditionRecord", List.of("id", "patient_id", "diagnosis_code", "condition_name", "clinical_status", "onset_date", "resolved_date", "note", "recorded_at")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.DepartmentRecord", List.of("id", "department_code", "department_name", "department_type", "is_active", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.RoomRecord", List.of("id", "department_id", "room_code", "room_name", "floor", "location_note", "room_type", "is_active")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.StaffRecord", List.of("id", "staff_code", "full_name", "staff_type", "license_number", "phone", "is_active")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.StaffDepartmentAssignmentRecord", List.of("id", "staff_id", "department_id", "is_primary", "valid_from", "valid_to")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.DiagnosisCatalogRecord", List.of("id", "diagnosis_code", "name_vi", "name_en", "parent_code", "is_active")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServiceRecord", List.of("id", "service_code", "service_name", "service_type", "performing_department_id", "default_room_id", "requires_payment", "requires_specimen", "health_examination_eligible", "result_type", "lab_panel_id", "preparation_instructions", "is_active", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.ServicePriceRecord", List.of("id", "service_id", "price_type", "payer_reference", "amount", "currency", "effective_from", "effective_to", "is_active")),
                Arguments.of("com.ngockhanh.clinic.catalog.infrastructure.persistence.record.MedicationRecord", List.of("id", "medication_code", "medication_name", "generic_name", "strength", "dosage_form", "default_route", "unit", "is_active")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.DocumentTemplateRecord", List.of("id", "template_code", "template_name", "template_type", "barcode_policy", "is_master_health_examination_form", "is_active", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.DocumentTemplateVersionRecord", List.of("id", "document_template_id", "version_number", "source_file_attachment_id", "paper_size", "custom_width_mm", "custom_height_mm", "orientation", "render_mode", "renderer_type", "schema_json", "render_template", "effective_from", "retired_at", "created_by_user_id", "created_at")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.DocumentTemplateFieldRecord", List.of("id", "document_template_version_id", "field_key", "display_label", "item_type", "selection_mode", "eligibility_rule_json", "display_order", "is_required")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.ServiceTemplateMappingRecord", List.of("id", "service_id", "document_template_id", "display_order", "is_active", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.GeneratedDocumentRecord", List.of("id", "encounter_id", "document_template_version_id", "document_kind", "print_sequence", "paper_size_snapshot", "orientation_snapshot", "version_number", "status", "render_payload_hash", "file_attachment_id", "generated_by_user_id", "generated_at", "invalidated_at")),
                Arguments.of("com.ngockhanh.clinic.document.infrastructure.persistence.record.GeneratedDocumentServiceRequestRecord", List.of("id", "generated_document_id", "service_request_id", "service_code_snapshot", "service_name_snapshot", "display_order", "created_at")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.AppointmentRecord", List.of("id", "patient_id", "source_encounter_id", "department_id", "doctor_staff_id", "scheduled_start", "scheduled_end", "status", "reason", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.EncounterRecord", List.of("id", "encounter_code", "patient_id", "encounter_type", "reason", "priority", "status", "started_at", "completed_at", "canceled_at", "created_at", "updated_at", "row_version", "prepared_at", "checked_in_at", "checked_in_by_user_id")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.EncounterAssignmentRecord", List.of("id", "encounter_id", "department_id", "room_id", "doctor_staff_id", "assigned_at", "ended_at", "assigned_by_user_id", "destination_label")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.VitalSignRecord", List.of("id", "encounter_id", "height_cm", "weight_kg", "bmi", "pulse_bpm", "systolic_bp", "diastolic_bp", "physical_classification", "measured_at", "recorded_by_staff_id")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.ClinicalNoteRecord", List.of("id", "encounter_id", "note_type", "content_json", "author_staff_id", "status", "created_at", "finalized_at")),
                Arguments.of("com.ngockhanh.clinic.encounter.infrastructure.persistence.record.EncounterDiagnosisRecord", List.of("id", "encounter_id", "diagnosis_catalog_id", "diagnosis_text", "diagnosis_type", "is_primary", "recorded_by_staff_id", "recorded_at")),
                Arguments.of("com.ngockhanh.clinic.clinical.infrastructure.persistence.record.OrderRoundRecord", List.of("id", "encounter_id", "health_examination_record_id", "round_number", "source_type", "status", "ordered_by_staff_id", "created_by_user_id", "ordered_at")),
                Arguments.of("com.ngockhanh.clinic.clinical.infrastructure.persistence.record.ServiceRequestRecord", List.of("id", "order_round_id", "service_id", "status", "priority", "performing_department_id", "performing_room_id", "performing_location_label", "service_name_snapshot", "unit_price_snapshot", "preparation_instructions_snapshot", "ordered_at", "started_at", "completed_at")),
                Arguments.of("com.ngockhanh.clinic.billing.infrastructure.persistence.record.PaymentAuthorizationRecord", List.of("id", "service_request_id", "status", "invoice_item_id", "authorized_at", "authorized_by_user_id", "reason", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.billing.infrastructure.persistence.record.InvoiceRecord", List.of("id", "invoice_number", "encounter_id", "patient_id", "invoice_type", "status", "subtotal", "discount_amount", "total_amount", "paid_amount", "issued_at", "created_by_user_id")),
                Arguments.of("com.ngockhanh.clinic.billing.infrastructure.persistence.record.InvoiceItemRecord", List.of("id", "invoice_id", "service_request_id", "service_id", "description_snapshot", "quantity", "unit_price", "discount_amount", "line_total")),
                Arguments.of("com.ngockhanh.clinic.billing.infrastructure.persistence.record.InvoiceAdjustmentRecord", List.of("id", "invoice_id", "adjustment_type", "amount", "reason", "created_by_user_id", "created_at")),
                Arguments.of("com.ngockhanh.clinic.billing.infrastructure.persistence.record.PaymentRecord", List.of("id", "invoice_id", "payment_method", "amount", "status", "gateway_transaction_id", "confirmed_by_user_id", "confirmed_at", "created_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyRecord", List.of("id", "company_code", "company_name", "tax_code", "address", "contact_name", "contact_phone", "contact_job_title", "note", "status", "created_at", "updated_at", "row_version")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyEmployeeRecord", List.of("id", "company_id", "patient_id", "employee_code", "identification_number", "full_name", "date_of_birth", "sex", "department_name", "job_title", "occupation", "status", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchRecord", List.of("id", "company_id", "batch_code", "batch_name", "start_date", "end_date", "reason", "payer_type", "examination_site_type", "examination_site_name", "examination_site_address", "master_template_version_id", "status", "finalized_at", "closed_at", "created_by_user_id", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchServiceRecord", List.of("id", "health_examination_batch_id", "service_id", "document_template_version_id", "service_code_snapshot", "service_name_snapshot", "base_price_snapshot", "negotiated_unit_price", "currency", "display_order", "status", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchEmployeeRecord", List.of("id", "health_examination_batch_id", "company_employee_id", "employee_code_snapshot", "department_snapshot", "job_title_snapshot", "occupation_snapshot", "administrative_snapshot_json", "status", "created_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationRecord", List.of("id", "shs_code", "source_type", "patient_id", "health_examination_batch_employee_id", "encounter_id", "master_template_version_id", "full_name_snapshot", "date_of_birth_snapshot", "sex_snapshot", "identification_number_snapshot", "identification_number_issue_date_snapshot", "identification_number_issue_place_snapshot", "ethnicity_snapshot", "subject_type_snapshot", "payer_source_snapshot", "blood_group_snapshot", "phone_snapshot", "province_snapshot", "ward_snapshot", "address_detail_snapshot", "occupation_snapshot", "workplace_or_school_snapshot", "health_examination_reason_snapshot", "planned_examination_date", "actual_examination_date", "status", "replaces_health_examination_record_id", "created_at", "completed_at", "canceled_at", "row_version")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchEmployeeServiceRecord", List.of("id", "health_examination_batch_employee_id", "health_examination_batch_service_id", "service_request_id", "billable", "unit_price_snapshot", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationImportJobRecord", List.of("id", "health_examination_batch_id", "import_type", "source_file_attachment_id", "status", "column_mapping_json", "total_rows", "valid_rows", "warning_rows", "error_rows", "created_by_user_id", "confirmed_by_user_id", "created_at", "confirmed_at")),
                Arguments.of("com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationImportRowRecord", List.of("id", "health_examination_import_job_id", "row_number", "employee_code_snapshot", "identification_number_snapshot", "service_code_snapshot", "validation_status", "error_codes_json", "normalized_payload_json", "resolved_patient_id", "resolved_company_employee_id", "resolved_batch_employee_id", "resolved_batch_service_id", "resolved_service_request_id")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.SpecimenRecord", List.of("id", "specimen_code", "patient_id", "encounter_id", "specimen_type", "status", "collected_at", "collected_by_staff_id", "received_at")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.SpecimenServiceRequestRecord", List.of("id", "specimen_id", "service_request_id")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.LabPanelRecord", List.of("id", "panel_code", "panel_name", "is_active")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.AnalyteRecord", List.of("id", "analyte_code", "analyte_name", "default_unit", "value_type", "is_active")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.LabPanelItemRecord", List.of("id", "lab_panel_id", "analyte_id", "display_order")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.AnalyteReferenceRangeRecord", List.of("id", "analyte_id", "sex", "age_min_days", "age_max_days", "lower_bound", "upper_bound", "text_range", "warning_lower", "warning_upper", "effective_from", "effective_to")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.LabResultRecord", List.of("id", "service_request_id", "specimen_id", "version_number", "status", "supersedes_lab_result_id", "result_source", "verified_by_staff_id", "verified_at", "finalized_at", "created_at", "raw_message_reference", "released_to_patient_at", "released_to_patient_by_user_id")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.LabResultValueRecord", List.of("id", "lab_result_id", "analyte_id", "numeric_value", "text_value", "unit_snapshot", "reference_range_snapshot", "abnormal_flag", "instrument_code", "measured_at")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.ImagingStudyRecord", List.of("id", "service_request_id", "modality", "external_study_uid", "device_identifier", "study_at", "metadata_json", "created_at")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.DiagnosticReportRecord", List.of("id", "service_request_id", "imaging_study_id", "document_template_version_id", "version_number", "status", "findings", "conclusion", "structured_data_json", "supersedes_report_id", "author_staff_id", "verified_by_staff_id", "finalized_at", "created_at", "released_to_patient_at", "released_to_patient_by_user_id")),
                Arguments.of("com.ngockhanh.clinic.diagnostics.infrastructure.persistence.record.FileAttachmentRecord", List.of("id", "patient_id", "encounter_id", "entity_type", "entity_id", "document_type", "storage_provider", "storage_key", "file_name", "mime_type", "size_bytes", "sha256", "created_by_user_id", "created_at")),
                Arguments.of("com.ngockhanh.clinic.prescription.infrastructure.persistence.record.PrescriptionRecord", List.of("id", "prescription_number", "encounter_id", "patient_id", "prescriber_staff_id", "version_number", "status", "supersedes_prescription_id", "issued_at", "created_at")),
                Arguments.of("com.ngockhanh.clinic.prescription.infrastructure.persistence.record.PrescriptionItemRecord", List.of("id", "prescription_id", "medication_id", "medication_name_snapshot", "strength_snapshot", "dose", "route", "frequency", "duration_days", "quantity", "instructions", "display_order")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.UserRecord", List.of("id", "principal_type", "staff_id", "patient_id", "auth_provider", "auth_subject", "status", "last_login_at", "created_at")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.RoleRecord", List.of("id", "role_code", "role_name", "is_active")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.PermissionRecord", List.of("id", "permission_code", "module", "description")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.UserRoleRecord", List.of("id", "user_id", "role_id", "department_id", "room_id", "valid_from", "valid_to")),
                Arguments.of("com.ngockhanh.clinic.identity.infrastructure.persistence.record.RolePermissionRecord", List.of("id", "role_id", "permission_id")),
                Arguments.of("com.ngockhanh.clinic.notification.infrastructure.persistence.record.NotificationRecord", List.of("id", "patient_id", "appointment_id", "encounter_id", "notification_type", "recipient_phone", "sms_template_code", "payload_json", "provider_code", "provider_message_id", "idempotency_key", "status", "attempt_count", "scheduled_at", "sent_at", "delivered_at", "failed_at", "next_retry_at", "error_code", "error_message", "created_at")),
                Arguments.of("com.ngockhanh.clinic.notification.infrastructure.persistence.record.NotificationAttemptRecord", List.of("id", "notification_id", "attempt_number", "provider_code", "provider_message_id", "status", "request_reference", "response_reference", "error_code", "error_message", "attempted_at")),
                Arguments.of("com.ngockhanh.clinic.shared.infrastructure.persistence.record.AuditLogRecord", List.of("id", "occurred_at", "actor_user_id", "action", "entity_type", "entity_id", "patient_id", "encounter_id", "health_examination_record_id", "correlation_id", "reason", "before_json", "after_json", "ip_address", "user_agent")),
                Arguments.of("com.ngockhanh.clinic.integration.infrastructure.persistence.record.IntegrationEndpointRecord", List.of("id", "endpoint_code", "integration_type", "endpoint_name", "base_url", "credential_reference", "config_json", "is_active", "created_at", "updated_at")),
                Arguments.of("com.ngockhanh.clinic.integration.infrastructure.persistence.record.ExternalCodeMappingRecord", List.of("id", "integration_endpoint_id", "mapping_type", "external_code", "internal_entity_type", "internal_entity_id", "valid_from", "valid_to")),
                Arguments.of("com.ngockhanh.clinic.integration.infrastructure.persistence.record.IntegrationMessageRecord", List.of("id", "integration_endpoint_id", "direction", "message_type", "external_message_id", "correlation_id", "status", "payload_reference", "payload_hash", "error_message", "received_at", "processed_at", "retry_count")),
                Arguments.of("com.ngockhanh.clinic.integration.infrastructure.persistence.record.IdempotencyKeyRecord", List.of("id", "scope", "idempotency_key", "request_hash", "result_entity_type", "result_entity_id", "status", "created_at", "expires_at")),
                Arguments.of("com.ngockhanh.clinic.integration.infrastructure.persistence.record.OutboxEventRecord", List.of("id", "aggregate_type", "aggregate_id", "event_type", "payload_json", "correlation_id", "status", "created_at", "published_at", "retry_count"))
        );
    }
}
