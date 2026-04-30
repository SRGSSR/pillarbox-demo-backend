DELETE FROM pb_session;

ALTER TABLE pb_session ADD COLUMN refresh_token TEXT;
ALTER TABLE pb_session ADD COLUMN id_token TEXT;
