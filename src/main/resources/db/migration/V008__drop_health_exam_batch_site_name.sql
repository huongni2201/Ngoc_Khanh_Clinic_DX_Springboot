-- Applies only to the current V001-V003 baseline. Earlier V004-V007 histories need a reviewed conversion.
-- Dropping this column removes SQL access to its values; old bytes may remain until rows are rewritten.
ALTER TABLE public.health_examination_batches DROP COLUMN examination_site_name;
