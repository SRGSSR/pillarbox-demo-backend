DELETE FROM pb_session;

CREATE TABLE IF NOT EXISTS pb_user (
  oidc_sub     VARCHAR(255) NOT NULL PRIMARY KEY,
  display_name VARCHAR(255) NOT NULL,
  updated_at   TIMESTAMPTZ  NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL
  );

ALTER TABLE pb_session DROP COLUMN last_checked;
ALTER TABLE pb_session ADD COLUMN refresh_token TEXT;
ALTER TABLE pb_session ADD COLUMN id_token TEXT;
ALTER TABLE pb_session ADD COLUMN oidc_sub VARCHAR(255) REFERENCES pb_user(oidc_sub);
