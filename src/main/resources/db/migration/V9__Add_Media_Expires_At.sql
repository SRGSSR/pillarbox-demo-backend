ALTER TABLE pb_media ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX pb_media_expires_at_idx ON pb_media (expires_at);
