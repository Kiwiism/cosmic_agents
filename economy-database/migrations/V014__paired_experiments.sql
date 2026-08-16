CREATE TABLE economy_experiment (
    experiment_id TEXT PRIMARY KEY,
    design TEXT NOT NULL CHECK (design = 'PAIRED_SAME_SEED'),
    description TEXT NOT NULL,
    manifest_yaml TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE economy_experiment_pair (
    experiment_id TEXT NOT NULL REFERENCES economy_experiment(experiment_id) ON DELETE CASCADE,
    pair_id TEXT NOT NULL,
    pair_order INTEGER NOT NULL CHECK (pair_order >= 0),
    seed BIGINT NOT NULL,
    baseline_run_id UUID NOT NULL UNIQUE,
    candidate_run_id UUID NOT NULL UNIQUE,
    baseline_config_path TEXT NOT NULL,
    candidate_config_path TEXT NOT NULL,
    baseline_config_hash CHAR(64) NOT NULL,
    candidate_config_hash CHAR(64) NOT NULL,
    catalog_version TEXT NOT NULL,
    PRIMARY KEY (experiment_id, pair_id),
    UNIQUE (experiment_id, pair_order)
);

CREATE INDEX economy_experiment_pair_seed_idx
    ON economy_experiment_pair(experiment_id, seed, pair_order);
