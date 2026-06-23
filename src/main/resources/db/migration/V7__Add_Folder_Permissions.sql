CREATE TABLE IF NOT EXISTS pb_folder_permission (
  id VARCHAR(255) PRIMARY KEY,
  folder_id VARCHAR(255) NOT NULL REFERENCES pb_folders(id) ON DELETE CASCADE,
  oidc_sub VARCHAR(255) REFERENCES pb_user(oidc_sub) ON DELETE CASCADE,
  team_id VARCHAR(255) REFERENCES pb_team(id) ON DELETE CASCADE,
  role VARCHAR(255),
  can_write BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CHECK (
    (CASE WHEN oidc_sub IS NULL THEN 0 ELSE 1 END
   + CASE WHEN team_id IS NULL THEN 0 ELSE 1 END
   + CASE WHEN role IS NULL THEN 0 ELSE 1 END) = 1
  ),
  UNIQUE NULLS NOT DISTINCT (folder_id, oidc_sub, team_id, role)
);

CREATE INDEX idx_folder_permission_folder ON pb_folder_permission(folder_id);
