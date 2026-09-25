/*
   Terminology cut-over for the corporate/adult Health Examination context.
   Existing Flyway migrations are immutable. This migration preserves rows and
   renames the existing tables, columns and database objects in place.
*/

IF OBJECT_ID(N'dbo.health_check_batches', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_batches', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_batches', N'health_examination_batches';

IF OBJECT_ID(N'dbo.health_check_batch_services', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_batch_services', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_batch_services', N'health_examination_batch_services';

IF OBJECT_ID(N'dbo.health_check_batch_employees', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_batch_employees', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_batch_employees', N'health_examination_batch_employees';

IF OBJECT_ID(N'dbo.health_check_batch_employee_services', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_batch_employee_services', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_batch_employee_services', N'health_examination_batch_employee_services';

IF OBJECT_ID(N'dbo.health_check_records', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_records', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_records', N'health_examination_records';

IF OBJECT_ID(N'dbo.health_check_import_jobs', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_import_jobs', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_import_jobs', N'health_examination_import_jobs';

IF OBJECT_ID(N'dbo.health_check_import_rows', N'U') IS NOT NULL
   AND OBJECT_ID(N'dbo.health_examination_import_rows', N'U') IS NULL
    EXEC sys.sp_rename N'dbo.health_check_import_rows', N'health_examination_import_rows';

DECLARE @columnRenames TABLE (
    table_name sysname NOT NULL,
    old_name sysname NOT NULL,
    new_name sysname NOT NULL
);

INSERT INTO @columnRenames (table_name, old_name, new_name)
VALUES
    (N'services', N'health_check_eligible', N'health_examination_eligible'),
    (N'document_templates', N'is_master_health_check_form', N'is_master_health_examination_form'),
    (N'appointments', N'doctor_staff_id', N'physician_staff_id'),
    (N'encounter_assignments', N'doctor_staff_id', N'physician_staff_id'),
    (N'order_rounds', N'health_check_record_id', N'health_examination_record_id'),
    (N'audit_logs', N'health_check_record_id', N'health_examination_record_id'),
    (N'health_examination_batch_services', N'health_check_batch_id', N'health_examination_batch_id'),
    (N'health_examination_batch_employees', N'health_check_batch_id', N'health_examination_batch_id'),
    (N'health_examination_records', N'health_check_batch_employee_id', N'health_examination_batch_employee_id'),
    (N'health_examination_records', N'health_check_reason_snapshot', N'health_examination_reason_snapshot'),
    (N'health_examination_records', N'replaces_health_check_record_id', N'replaces_health_examination_record_id'),
    (N'health_examination_batch_employee_services', N'health_check_batch_employee_id', N'health_examination_batch_employee_id'),
    (N'health_examination_batch_employee_services', N'health_check_batch_service_id', N'health_examination_batch_service_id'),
    (N'health_examination_import_jobs', N'health_check_batch_id', N'health_examination_batch_id'),
    (N'health_examination_import_rows', N'health_check_import_job_id', N'health_examination_import_job_id');

DECLARE @tableName sysname, @oldColumn sysname, @newColumn sysname, @renameSql nvarchar(1000);
DECLARE column_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT table_name, old_name, new_name FROM @columnRenames;

OPEN column_cursor;
FETCH NEXT FROM column_cursor INTO @tableName, @oldColumn, @newColumn;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF COL_LENGTH(N'dbo.' + @tableName, @oldColumn) IS NOT NULL
       AND COL_LENGTH(N'dbo.' + @tableName, @newColumn) IS NULL
    BEGIN
        SET @renameSql = N'EXEC sys.sp_rename N''dbo.' + REPLACE(@tableName, N'''', N'''''') + N'.' + REPLACE(@oldColumn, N'''', N'''''')
            + N''', N''' + REPLACE(@newColumn, N'''', N'''''') + N''', N''COLUMN''';
        EXEC sys.sp_executesql @renameSql;
    END;

    FETCH NEXT FROM column_cursor INTO @tableName, @oldColumn, @newColumn;
END;
CLOSE column_cursor;
DEALLOCATE column_cursor;

DECLARE @schemaName sysname, @objectName sysname, @renamedObject sysname;
DECLARE object_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT s.name, o.name, REPLACE(o.name, N'health_check', N'health_examination')
    FROM sys.objects o
    JOIN sys.schemas s ON s.schema_id = o.schema_id
    WHERE s.name = N'dbo'
      AND o.name LIKE N'%health_check%'
      AND o.type IN ('C', 'D', 'F', 'PK', 'UQ');

OPEN object_cursor;
FETCH NEXT FROM object_cursor INTO @schemaName, @objectName, @renamedObject;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys.objects
        WHERE schema_id = SCHEMA_ID(@schemaName) AND name = @renamedObject
    )
    BEGIN
        SET @renameSql = N'EXEC sys.sp_rename N''' + QUOTENAME(@schemaName) + N'.' + QUOTENAME(@objectName)
            + N''', N''' + REPLACE(@renamedObject, N'''', N'''''') + N''', N''OBJECT''';
        EXEC sys.sp_executesql @renameSql;
    END;

    FETCH NEXT FROM object_cursor INTO @schemaName, @objectName, @renamedObject;
END;
CLOSE object_cursor;
DEALLOCATE object_cursor;

DECLARE @indexTable sysname, @indexName sysname, @renamedIndex sysname;
DECLARE index_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT OBJECT_NAME(i.object_id), i.name, REPLACE(i.name, N'health_check', N'health_examination')
    FROM sys.indexes i
    WHERE i.name LIKE N'%health_check%'
      AND i.is_hypothetical = 0;

OPEN index_cursor;
FETCH NEXT FROM index_cursor INTO @indexTable, @indexName, @renamedIndex;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes i
        WHERE i.object_id = OBJECT_ID(N'dbo.' + @indexTable) AND i.name = @renamedIndex
    )
    BEGIN
        SET @renameSql = N'EXEC sys.sp_rename N''dbo.' + REPLACE(@indexTable, N'''', N'''''') + N'.' + QUOTENAME(@indexName)
            + N''', N''' + REPLACE(@renamedIndex, N'''', N'''''') + N''', N''INDEX''';
        EXEC sys.sp_executesql @renameSql;
    END;

    FETCH NEXT FROM index_cursor INTO @indexTable, @indexName, @renamedIndex;
END;
CLOSE index_cursor;
DEALLOCATE index_cursor;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.health_examination_batch_employee_services') AND name = N'IX_hcbes_employee')
    EXEC sys.sp_rename N'dbo.health_examination_batch_employee_services.IX_hcbes_employee', N'IX_health_examination_batch_employee_services_employee', N'INDEX';

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.health_examination_batch_employee_services') AND name = N'IX_hcbes_service')
    EXEC sys.sp_rename N'dbo.health_examination_batch_employee_services.IX_hcbes_service', N'IX_health_examination_batch_employee_services_service', N'INDEX';

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.health_examination_batch_employee_services') AND name = N'UX_hcbes_service_request')
    EXEC sys.sp_rename N'dbo.health_examination_batch_employee_services.UX_hcbes_service_request', N'UX_health_examination_batch_employee_services_service_request', N'INDEX';
