-- Read-only precheck for the V002 Journey cut-over. Returns only table names and row counts.
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.journeys', N'U') IS NULL
    SELECT N'dbo.journeys' AS table_name, CAST(NULL AS bigint) AS row_count, N'ABSENT' AS table_state;
ELSE
    EXEC sys.sp_executesql N'SELECT N''dbo.journeys'' AS table_name, COUNT_BIG(*) AS row_count, N''PRESENT'' AS table_state FROM dbo.journeys;';

IF OBJECT_ID(N'dbo.journey_events', N'U') IS NULL
    SELECT N'dbo.journey_events' AS table_name, CAST(NULL AS bigint) AS row_count, N'ABSENT' AS table_state;
ELSE
    EXEC sys.sp_executesql N'SELECT N''dbo.journey_events'' AS table_name, COUNT_BIG(*) AS row_count, N''PRESENT'' AS table_state FROM dbo.journey_events;';
