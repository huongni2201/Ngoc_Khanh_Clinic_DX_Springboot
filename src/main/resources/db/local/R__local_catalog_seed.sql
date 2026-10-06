-- Local development catalog data (profile "local" only, via classpath:db/local).
-- Repeatable and idempotent: rows are keyed by their unique code and never overwritten.
-- It seeds reference data only: no account, role, staff or credential is created here.

INSERT INTO public.departments (id, code, name, department_type, active)
VALUES
    ('01990000-0000-7000-8000-000000000101', 'LOCAL_DEPT_CLINICAL', 'Khoa khám bệnh (local)', 'CLINICAL', true),
    ('01990000-0000-7000-8000-000000000102', 'LOCAL_DEPT_DIAGNOSTIC', 'Khoa chẩn đoán hình ảnh (local)', 'DIAGNOSTIC', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.organizations (
    id, name, tax_code, phone, email, address,
    contact_full_name, contact_phone, contact_email, status
)
VALUES
    ('01990000-0000-7000-8000-000000000301', 'LOCAL TEST - Công ty An Phú', 'LOCAL_TEST_ORG_001', '0000000001', 'org-001@example.invalid', 'Địa chỉ thử nghiệm 01, TP. Hồ Chí Minh', 'Local Contact 001', '0000000001', 'contact-001@example.invalid', 'ACTIVE'),
    ('01990000-0000-7000-8000-000000000302', 'LOCAL TEST - Trường Bình Minh', 'LOCAL_TEST_ORG_002', '0000000002', 'org-002@example.invalid', 'Địa chỉ thử nghiệm 02, TP. Hồ Chí Minh', 'Local Contact 002', '0000000002', 'contact-002@example.invalid', 'ACTIVE'),
    ('01990000-0000-7000-8000-000000000303', 'LOCAL TEST - Nhà máy Hòa Bình', 'LOCAL_TEST_ORG_003', '0000000003', 'org-003@example.invalid', 'Địa chỉ thử nghiệm 03, TP. Hồ Chí Minh', 'Local Contact 003', '0000000003', 'contact-003@example.invalid', 'ACTIVE')
ON CONFLICT (tax_code) DO NOTHING;

INSERT INTO public.services (id, code, name, service_type, performing_department_id, unit_price, active)
SELECT v.id::uuid, v.code, v.name, v.service_type, d.id, v.unit_price, true
FROM (
    VALUES
        ('01990000-0000-7000-8000-000000000201', 'LOCAL_SVC_CONSULT', 'Khám tổng quát', 'CONSULTATION', 'LOCAL_DEPT_CLINICAL', 150000.00),
        ('01990000-0000-7000-8000-000000000202', 'LOCAL_SVC_LAB_BLOOD', 'Xét nghiệm công thức máu', 'LAB', 'LOCAL_DEPT_DIAGNOSTIC', 90000.00),
        ('01990000-0000-7000-8000-000000000203', 'LOCAL_SVC_ULTRASOUND', 'Siêu âm ổ bụng', 'ULTRASOUND', 'LOCAL_DEPT_DIAGNOSTIC', 180000.00),
        ('01990000-0000-7000-8000-000000000204', 'LOCAL_SVC_XRAY_CHEST', 'X-quang ngực thẳng', 'XRAY', 'LOCAL_DEPT_DIAGNOSTIC', 120000.00),
        ('01990000-0000-7000-8000-000000000205', 'LOCAL_SVC_ECG', 'Điện tâm đồ', 'ECG', 'LOCAL_DEPT_DIAGNOSTIC', 100000.00)
) AS v(id, code, name, service_type, department_code, unit_price)
JOIN public.departments d ON d.code = v.department_code
ON CONFLICT (code) DO NOTHING;
