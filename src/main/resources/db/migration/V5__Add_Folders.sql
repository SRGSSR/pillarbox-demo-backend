CREATE TABLE IF NOT EXISTS pb_folders (
  id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  parent_id VARCHAR(255) REFERENCES pb_folders(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE NULLS NOT DISTINCT (parent_id, name)
  );

CREATE INDEX idx_folder_parent ON pb_folders(parent_id);

CREATE RECURSIVE VIEW v_folder_ancestors (
    descendant_id, id, name, parent_id, created_at, updated_at, depth
) AS
    SELECT id, id, name, parent_id, created_at, updated_at, 1
    FROM pb_folders

    UNION ALL

    SELECT v.descendant_id, f.id, f.name, f.parent_id, f.created_at, f.updated_at, v.depth + 1
    FROM pb_folders f
    JOIN v_folder_ancestors v ON f.id = v.parent_id;

CREATE TABLE IF NOT EXISTS pb_folder_media (
  folder_id  VARCHAR(255) NOT NULL REFERENCES pb_folders(id) ON DELETE CASCADE,
  media_id   VARCHAR(255) NOT NULL REFERENCES pb_media(id) ON DELETE CASCADE,
  added_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  PRIMARY KEY (media_id)
);
