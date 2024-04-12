CREATE TABLE IF NOT EXISTS cache
(
    id                 SERIAL PRIMARY KEY,
    key                text NOT NULL,
    value              text NOT NULL
);

CREATE INDEX cache_key_idx ON cache(key);
