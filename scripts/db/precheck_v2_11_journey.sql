-- Read-only PostgreSQL precheck after V001. Returns table names and row counts only.
SELECT 'public.journeys' AS table_name, count(*)::bigint AS row_count FROM public.journeys
UNION ALL
SELECT 'public.journey_events' AS table_name, count(*)::bigint AS row_count FROM public.journey_events;
