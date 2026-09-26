ALTER TABLE public.lab_results
ADD released_to_patient_at timestamptz(3) NULL,
    released_to_patient_by_user_id uuid NULL;

ALTER TABLE public.lab_results
ADD CONSTRAINT FK_lab_results_released_to_patient_by_user_id
FOREIGN KEY (released_to_patient_by_user_id)
REFERENCES public.users(id);

ALTER TABLE public.diagnostic_reports
ADD released_to_patient_at timestamptz(3) NULL,
    released_to_patient_by_user_id uuid NULL;

ALTER TABLE public.diagnostic_reports
ADD CONSTRAINT FK_diagnostic_reports_released_to_patient_by_user_id
FOREIGN KEY (released_to_patient_by_user_id)
REFERENCES public.users(id);
