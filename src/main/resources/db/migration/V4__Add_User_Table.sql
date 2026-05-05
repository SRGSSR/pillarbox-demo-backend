CREATE TABLE IF NOT EXISTS pb_user (
  user_id      VARCHAR(255) PRIMARY KEY,
  oidc_sub     VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255) NOT NULL,
  updated_at   TIMESTAMPTZ  NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL,
  last_login_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE pb_session ADD COLUMN user_id VARCHAR(255) REFERENCES pb_user(user_id);
