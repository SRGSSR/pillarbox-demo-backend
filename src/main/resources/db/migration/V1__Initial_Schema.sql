CREATE TABLE IF NOT EXISTS pb_media (
  id VARCHAR(255) PRIMARY KEY,
  tags TEXT[] NOT NULL,
  sources JSONB NOT NULL,
  drm_configs JSONB NOT NULL,
  metadata JSONB NOT NULL
  );
