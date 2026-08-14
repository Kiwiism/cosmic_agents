ALTER TABLE simulation_run DROP CONSTRAINT simulation_run_status_check;

ALTER TABLE simulation_run ADD CONSTRAINT simulation_run_status_check CHECK (status IN (
    'CREATED', 'RUNNING', 'WAITING_PHYSICAL_ACTION', 'INVARIANT_VIOLATION',
    'COMPLETED', 'FAILED', 'STOPPED'
));
