CREATE FUNCTION pb_immutable_array_to_string(arr text[]) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE AS
$$
SELECT array_to_string(arr, ' ') $$;

ALTER TABLE pb_media
  ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
      setweight(to_tsvector('simple', coalesce(metadata ->> 'title', '')), 'A') ||
      setweight(to_tsvector('simple', coalesce(id, '')), 'A') ||
      setweight(to_tsvector('simple', pb_immutable_array_to_string(tags)), 'B') ||
      setweight(to_tsvector('simple', coalesce(metadata ->> 'subtitle', '')), 'C') ||
      setweight(to_tsvector('simple', coalesce(metadata ->> 'description', '')), 'D')
      ) STORED;

CREATE INDEX idx_pb_media_search ON pb_media USING GIN (search_vector);
