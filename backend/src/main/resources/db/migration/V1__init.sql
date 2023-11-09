CREATE TABLE environment (
  id bigserial PRIMARY KEY,
  name text NOT NULL UNIQUE,
  sdk_key_hash text NOT NULL,
  sdk_key_prefix text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE flag (
  id bigserial PRIMARY KEY,
  flag_key text NOT NULL UNIQUE CHECK (flag_key ~ '^[a-z0-9][a-z0-9._-]{0,99}$'),
  name text NOT NULL,
  description text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE flag_environment (
  flag_id bigint NOT NULL REFERENCES flag(id) ON DELETE CASCADE,
  environment_id bigint NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
  enabled boolean NOT NULL DEFAULT false,
  rollout_percentage int NOT NULL DEFAULT 100 CHECK (rollout_percentage BETWEEN 0 AND 100),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (flag_id, environment_id)
);

CREATE TABLE audit_log (
  id bigserial PRIMARY KEY,
  at timestamptz NOT NULL DEFAULT now(),
  flag_key text NOT NULL,
  environment text,
  action text NOT NULL,
  actor text NOT NULL,
  detail text NOT NULL
);
CREATE INDEX audit_log_flag_idx ON audit_log (flag_key, at DESC);
CREATE INDEX audit_log_at_idx ON audit_log (at DESC);
