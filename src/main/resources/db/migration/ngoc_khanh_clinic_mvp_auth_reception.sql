/*
  Ngoc Khanh Clinic - MVP Database Schema
  Database: Microsoft SQL Server

  MVP scope covered by this schema:
    A. Authentication / basic RBAC
       - roles
       - users
       - user_roles

    B. Reception
       - patient registry
       - corporate employee -> patient link at check-in
       - encounter/check-in record

    C. Corporate health check
       - enterprises
       - clinic service catalog
       - health-check batches
       - service + unit-price snapshot per batch
       - canonical corporate employee import (15 business fields; STT not persisted)
       - per-employee service assignment/completion

  Intentionally NOT included yet:
    - password reset / MFA / auth session persistence
    - permissions matrix finer than role
    - doctor clinical findings / medical conclusions
    - lab / imaging result detail
    - room routing / queue routing by room
    - billing / invoice / payment
    - print-template configuration
    - audit log

  Canonical corporate employee import rule:
    - Template must contain all required COLUMNS.
    - Cell values may be blank and are stored as NULL.
    - STT is presentation/import-row metadata only and is NOT persisted.
*/

SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;
GO

/* =========================================================
   1. ROLES
   MVP roles are deliberately simple. More roles can be added later.
   ========================================================= */
CREATE TABLE dbo.roles (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    code                VARCHAR(50) NOT NULL,
    name                NVARCHAR(100) NOT NULL,
    description         NVARCHAR(500) NULL,
    is_active           BIT NOT NULL CONSTRAINT DF_roles_is_active DEFAULT (1),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_roles_created_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_roles PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_roles_code UNIQUE (code)
);
GO

INSERT INTO dbo.roles(code, name, description)
VALUES
    ('ADMIN', N'Quản trị hệ thống', N'Quản trị cấu hình và người dùng'),
    ('RECEPTIONIST', N'Lễ tân', N'Tiếp nhận bệnh nhân, check-in và quản lý thông tin hành chính');
GO

/* =========================================================
   2. USERS
   password_hash stores an encoded password hash (e.g. BCrypt/Argon2),
   NEVER plaintext.
   ========================================================= */
CREATE TABLE dbo.users (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    username            VARCHAR(100) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           NVARCHAR(200) NOT NULL,
    email               VARCHAR(255) NULL,
    phone               VARCHAR(30) NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_users_status DEFAULT ('ACTIVE'),
    last_login_at       DATETIME2(0) NULL,
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_users_created_at DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_users_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_users PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_users_username UNIQUE (username),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
);
GO

CREATE UNIQUE INDEX UX_users_email
    ON dbo.users(email)
    WHERE email IS NOT NULL;
GO

/* =========================================================
   3. USER ROLES
   Many-to-many keeps the model extensible without complicating MVP UI.
   ========================================================= */
CREATE TABLE dbo.user_roles (
    user_id             BIGINT NOT NULL,
    role_id             BIGINT NOT NULL,
    assigned_at         DATETIME2(0) NOT NULL CONSTRAINT DF_user_roles_assigned_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_user_roles PRIMARY KEY CLUSTERED (user_id, role_id),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_user_roles_role FOREIGN KEY (role_id) REFERENCES dbo.roles(id)
);
GO

CREATE INDEX IX_user_roles_role ON dbo.user_roles(role_id);
GO

/* =========================================================
   4. ENTERPRISES
   ========================================================= */
CREATE TABLE dbo.enterprises (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    code                VARCHAR(30) NOT NULL,
    name                NVARCHAR(255) NOT NULL,
    tax_code            VARCHAR(50) NULL,
    contact_name        NVARCHAR(150) NULL,
    contact_phone       VARCHAR(30) NULL,
    contact_email       VARCHAR(255) NULL,
    address             NVARCHAR(500) NULL,
    note                NVARCHAR(1000) NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_enterprises_status DEFAULT ('ACTIVE'),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_enterprises_created_at DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_enterprises_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_enterprises PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_enterprises_code UNIQUE (code),
    CONSTRAINT CK_enterprises_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
GO

CREATE INDEX IX_enterprises_name ON dbo.enterprises(name);
CREATE INDEX IX_enterprises_tax_code ON dbo.enterprises(tax_code) WHERE tax_code IS NOT NULL;
GO

/* =========================================================
   5. SERVICE CATALOG
   One catalog for the whole clinic.
   ========================================================= */
CREATE TABLE dbo.services (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    code                VARCHAR(30) NOT NULL,
    name                NVARCHAR(255) NOT NULL,
    default_price       DECIMAL(18,2) NULL,
    description         NVARCHAR(1000) NULL,
    is_active           BIT NOT NULL CONSTRAINT DF_services_is_active DEFAULT (1),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_services_created_at DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_services_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_services PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_services_code UNIQUE (code),
    CONSTRAINT CK_services_default_price CHECK (default_price IS NULL OR default_price >= 0)
);
GO

CREATE INDEX IX_services_name ON dbo.services(name);
GO

/* =========================================================
   6. PATIENTS
   Patient registry is separate from imported corporate employees.
   Corporate employees become linked to a patient ONLY when needed,
   typically at reception/check-in.
   ========================================================= */
CREATE TABLE dbo.patients (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    code                    VARCHAR(30) NOT NULL,

    full_name               NVARCHAR(200) NOT NULL,
    gender                  NVARCHAR(20) NULL,
    date_of_birth           DATE NULL,
    phone                   VARCHAR(30) NULL,

    identity_number         VARCHAR(50) NULL,
    identity_issue_date     DATE NULL,
    identity_issue_place    NVARCHAR(255) NULL,

    ethnicity               NVARCHAR(100) NULL,
    blood_group             NVARCHAR(20) NULL,
    occupation              NVARCHAR(255) NULL,
    workplace               NVARCHAR(500) NULL,
    current_address         NVARCHAR(500) NULL,

    note                    NVARCHAR(1000) NULL,
    status                  VARCHAR(20) NOT NULL CONSTRAINT DF_patients_status DEFAULT ('ACTIVE'),
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_patients_created_at DEFAULT (SYSDATETIME()),
    updated_at              DATETIME2(0) NOT NULL CONSTRAINT DF_patients_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_patients PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_patients_code UNIQUE (code),
    CONSTRAINT CK_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
GO

CREATE UNIQUE INDEX UX_patients_identity_number
    ON dbo.patients(identity_number)
    WHERE identity_number IS NOT NULL;

CREATE INDEX IX_patients_phone
    ON dbo.patients(phone)
    WHERE phone IS NOT NULL;

CREATE INDEX IX_patients_name_dob
    ON dbo.patients(full_name, date_of_birth);
GO

/* =========================================================
   7. CORPORATE HEALTH-CHECK BATCHES
   ========================================================= */
CREATE TABLE dbo.health_check_batches (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    enterprise_id       BIGINT NOT NULL,
    code                VARCHAR(30) NOT NULL,
    name                NVARCHAR(255) NOT NULL,
    exam_date           DATE NOT NULL,
    location            NVARCHAR(500) NOT NULL,
    note                NVARCHAR(1000) NULL,
    status              VARCHAR(20) NOT NULL CONSTRAINT DF_health_check_batches_status DEFAULT ('IN_PROGRESS'),
    created_at          DATETIME2(0) NOT NULL CONSTRAINT DF_health_check_batches_created_at DEFAULT (SYSDATETIME()),
    updated_at          DATETIME2(0) NOT NULL CONSTRAINT DF_health_check_batches_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_health_check_batches PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_health_check_batches_code UNIQUE (code),
    CONSTRAINT FK_health_check_batches_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES dbo.enterprises(id),
    CONSTRAINT CK_health_check_batches_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
GO

CREATE INDEX IX_health_check_batches_enterprise_date
    ON dbo.health_check_batches(enterprise_id, exam_date DESC);
CREATE INDEX IX_health_check_batches_status
    ON dbo.health_check_batches(status);
GO

/* =========================================================
   8. SERVICES CONFIGURED FOR EACH BATCH
   unit_price is a SNAPSHOT for that batch.
   ========================================================= */
CREATE TABLE dbo.health_check_batch_services (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    batch_id                BIGINT NOT NULL,
    service_id              BIGINT NOT NULL,
    service_name_snapshot   NVARCHAR(255) NOT NULL,
    unit_price              DECIMAL(18,2) NOT NULL,
    display_order           INT NULL,
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_hc_batch_services_created_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_health_check_batch_services PRIMARY KEY CLUSTERED (id),
    CONSTRAINT FK_hc_batch_services_batch
        FOREIGN KEY (batch_id) REFERENCES dbo.health_check_batches(id),
    CONSTRAINT FK_hc_batch_services_service
        FOREIGN KEY (service_id) REFERENCES dbo.services(id),
    CONSTRAINT UQ_hc_batch_services UNIQUE (batch_id, service_id),
    CONSTRAINT CK_hc_batch_services_unit_price CHECK (unit_price > 0)
);
GO

CREATE INDEX IX_hc_batch_services_batch
    ON dbo.health_check_batch_services(batch_id);
CREATE INDEX IX_hc_batch_services_service
    ON dbo.health_check_batch_services(service_id);
GO

/* =========================================================
   9. EMPLOYEES IMPORTED INTO A BATCH

   Canonical Excel template business columns:
     1  Ho va ten
     2  Gioi tinh
     3  Ngay/thang/nam sinh
     4  So dien thoai
     5  So CCCD/Ho chieu/Ma dinh danh
     6  Cap ngay
     7  Noi cap
     8  Dan toc
     9  Doi tuong
     10 Nhom mau (neu co)
     11 Nghe nghiep
     12 Noi lam viec
     13 Cho o hien tai
     14 Nguon chi tra
     15 Ghi chu

   STT is NOT stored.
   Template columns are strict; CELL values may be NULL.

   patient_id is NULL after import and is populated when reception links
   this corporate employee to an existing/new patient.
   ========================================================= */
CREATE TABLE dbo.health_check_batch_employees (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    batch_id                BIGINT NOT NULL,
    patient_id              BIGINT NULL,

    full_name               NVARCHAR(200) NULL,
    gender                  NVARCHAR(20) NULL,
    date_of_birth           DATE NULL,
    phone                   VARCHAR(30) NULL,

    identity_number         VARCHAR(50) NULL,
    identity_issue_date     DATE NULL,
    identity_issue_place    NVARCHAR(255) NULL,

    ethnicity               NVARCHAR(100) NULL,
    subject_type            NVARCHAR(150) NULL,
    blood_group             NVARCHAR(20) NULL,

    occupation              NVARCHAR(255) NULL,
    workplace               NVARCHAR(500) NULL,
    current_address         NVARCHAR(500) NULL,

    payer_source            NVARCHAR(255) NULL,
    note                    NVARCHAR(1000) NULL,

    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_hc_batch_employees_created_at DEFAULT (SYSDATETIME()),
    updated_at              DATETIME2(0) NOT NULL CONSTRAINT DF_hc_batch_employees_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_health_check_batch_employees PRIMARY KEY CLUSTERED (id),
    CONSTRAINT FK_hc_batch_employees_batch
        FOREIGN KEY (batch_id) REFERENCES dbo.health_check_batches(id),
    CONSTRAINT FK_hc_batch_employees_patient
        FOREIGN KEY (patient_id) REFERENCES dbo.patients(id)
);
GO

CREATE INDEX IX_hc_batch_employees_batch
    ON dbo.health_check_batch_employees(batch_id);
CREATE INDEX IX_hc_batch_employees_patient
    ON dbo.health_check_batch_employees(patient_id)
    WHERE patient_id IS NOT NULL;
CREATE INDEX IX_hc_batch_employees_batch_name
    ON dbo.health_check_batch_employees(batch_id, full_name);
CREATE INDEX IX_hc_batch_employees_batch_phone
    ON dbo.health_check_batch_employees(batch_id, phone)
    WHERE phone IS NOT NULL;
CREATE INDEX IX_hc_batch_employees_batch_identity
    ON dbo.health_check_batch_employees(batch_id, identity_number)
    WHERE identity_number IS NOT NULL;
GO

/* One non-null identity cannot appear twice inside the same batch. */
CREATE UNIQUE INDEX UX_hc_batch_employees_batch_identity
    ON dbo.health_check_batch_employees(batch_id, identity_number)
    WHERE identity_number IS NOT NULL;
GO

/* =========================================================
   10. PER-EMPLOYEE SERVICE ASSIGNMENT / COMPLETION

   Row exists = this service is assigned/selected for this employee.
   No row      = this service is not selected for this employee.

   Tab "Chi tiet kham": status = COMPLETED => display X.
   Tab "Bao cao": count COMPLETED rows grouped by batch_service_id.
   ========================================================= */
CREATE TABLE dbo.health_check_batch_employee_services (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    batch_employee_id       BIGINT NOT NULL,
    batch_service_id        BIGINT NOT NULL,
    status                  VARCHAR(20) NOT NULL CONSTRAINT DF_hc_employee_services_status DEFAULT ('PENDING'),
    completed_at            DATETIME2(0) NULL,
    note                    NVARCHAR(1000) NULL,
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_hc_employee_services_created_at DEFAULT (SYSDATETIME()),
    updated_at              DATETIME2(0) NOT NULL CONSTRAINT DF_hc_employee_services_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_health_check_batch_employee_services PRIMARY KEY CLUSTERED (id),
    CONSTRAINT FK_hc_employee_services_employee
        FOREIGN KEY (batch_employee_id) REFERENCES dbo.health_check_batch_employees(id),
    CONSTRAINT FK_hc_employee_services_batch_service
        FOREIGN KEY (batch_service_id) REFERENCES dbo.health_check_batch_services(id),
    CONSTRAINT UQ_hc_employee_services UNIQUE (batch_employee_id, batch_service_id),
    CONSTRAINT CK_hc_employee_services_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT CK_hc_employee_services_completed_at
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR
            (status <> 'COMPLETED')
        )
);
GO

CREATE INDEX IX_hc_employee_services_employee_status
    ON dbo.health_check_batch_employee_services(batch_employee_id, status);

CREATE INDEX IX_hc_employee_services_batch_service_status
    ON dbo.health_check_batch_employee_services(batch_service_id, status);
GO

/* =========================================================
   11. ENCOUNTERS / RECEPTION CHECK-IN

   Reception flow:
     - Search patient by identity/phone/name + DOB
     - If patient exists: reuse it
     - Else: create a patient
     - For corporate employee: set health_check_batch_employees.patient_id
     - Create an encounter/check-in

   source_type:
     CORPORATE = created from a corporate batch employee
     WALK_IN   = normal reception registration
   ========================================================= */
CREATE TABLE dbo.encounters (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    code                    VARCHAR(30) NOT NULL,
    patient_id              BIGINT NOT NULL,
    batch_employee_id       BIGINT NULL,
    source_type             VARCHAR(20) NOT NULL,

    status                  VARCHAR(20) NOT NULL CONSTRAINT DF_encounters_status DEFAULT ('WAITING'),
    queue_number            INT NULL,
    check_in_at             DATETIME2(0) NOT NULL CONSTRAINT DF_encounters_check_in_at DEFAULT (SYSDATETIME()),
    completed_at            DATETIME2(0) NULL,
    note                    NVARCHAR(1000) NULL,

    created_by_user_id      BIGINT NOT NULL,
    created_at              DATETIME2(0) NOT NULL CONSTRAINT DF_encounters_created_at DEFAULT (SYSDATETIME()),
    updated_at              DATETIME2(0) NOT NULL CONSTRAINT DF_encounters_updated_at DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_encounters PRIMARY KEY CLUSTERED (id),
    CONSTRAINT UQ_encounters_code UNIQUE (code),
    CONSTRAINT FK_encounters_patient
        FOREIGN KEY (patient_id) REFERENCES dbo.patients(id),
    CONSTRAINT FK_encounters_batch_employee
        FOREIGN KEY (batch_employee_id) REFERENCES dbo.health_check_batch_employees(id),
    CONSTRAINT FK_encounters_created_by_user
        FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id),

    CONSTRAINT CK_encounters_source_type
        CHECK (source_type IN ('CORPORATE', 'WALK_IN')),
    CONSTRAINT CK_encounters_status
        CHECK (status IN ('REGISTERED', 'WAITING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT CK_encounters_source_reference
        CHECK (
            (source_type = 'CORPORATE' AND batch_employee_id IS NOT NULL)
            OR
            (source_type = 'WALK_IN' AND batch_employee_id IS NULL)
        ),
    CONSTRAINT CK_encounters_completed_at
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR
            (status <> 'COMPLETED')
        )
);
GO

CREATE INDEX IX_encounters_patient_checkin
    ON dbo.encounters(patient_id, check_in_at DESC);
CREATE INDEX IX_encounters_status_checkin
    ON dbo.encounters(status, check_in_at DESC);
CREATE INDEX IX_encounters_batch_employee
    ON dbo.encounters(batch_employee_id)
    WHERE batch_employee_id IS NOT NULL;
GO

/* =========================================================
   APPLICATION / DOMAIN RULES FOR MVP
   =========================================================

   AUTHENTICATION
   --------------
   - Never store plaintext password.
   - Use password_hash with Spring Security PasswordEncoder.
   - Login by users.username.
   - Roles are authorization claims; backend authorization is authoritative.
   - No auth session table is required for MVP if using stateless access/refresh JWT.
     Add token/session persistence only when revocation/device-session requirements are confirmed.

   CORPORATE IMPORT
   ----------------
   - Missing required COLUMN in template => reject file.
   - Blank CELL => store NULL.
   - Ignore fully blank rows.
   - STT is not persisted.
   - phone and identity_number must be parsed as text to preserve leading zeros.
   - Normalize dates to SQL DATE.

   CORPORATE SERVICES
   ------------------
   - health_check_batch_services.unit_price is the historical price snapshot.
   - Only selected batch services are inserted for a batch.
   - A batch employee may receive a subset of configured services.
   - health_check_batch_employee_services is the source of truth for actual completion.

   RECEPTION
   ---------
   - Imported corporate employee != Patient.
   - Import must NOT auto-create all patients.
   - At check-in, search patient first; reuse if found, create if not found.
   - Link the corporate row with health_check_batch_employees.patient_id.
   - Create encounters row for each actual reception/check-in.

   REPORTING
   ---------
   - Do not persist report totals for MVP.
   - completed_count = count(employee_service WHERE status = 'COMPLETED').
   - subtotal = completed_count * batch_service.unit_price.
   - grand_total = SUM(subtotal).

   UPDATED_AT
   ----------
   - SQL Server does not auto-update updated_at.
   - Application/service layer must set updated_at = SYSDATETIME() on UPDATE.

   DELETION
   --------
   - FKs intentionally do not use ON DELETE CASCADE.
   - Prefer state transitions / controlled service-layer deletion for clinic data.
*/

COMMIT TRANSACTION;
GO
