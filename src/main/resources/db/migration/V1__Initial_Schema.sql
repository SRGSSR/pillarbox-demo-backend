CREATE TABLE IF NOT EXISTS pb_media (
  id VARCHAR(255) PRIMARY KEY,
  tags TEXT[] NOT NULL,
  sources JSONB NOT NULL,
  drm_configs JSONB NOT NULL,
  metadata JSONB NOT NULL
  );

CREATE TABLE IF NOT EXISTS pb_session (
  session_id VARCHAR(255) PRIMARY KEY,
  access_token TEXT NOT NULL,
  last_checked TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
  );

CREATE INDEX IF NOT EXISTS idx_pb_session_expires ON pb_session (expires_at);
