-- Create table to store historical rating data for players
CREATE TABLE IF NOT EXISTS rating_history
(
    player_id       INTEGER NOT NULL,
    month           INTEGER NOT NULL, -- Epoch-based month index: (year - 1970) * 12 + (month - 1)
    standard        INTEGER,
    standard_k      INTEGER,
    rapid           INTEGER,
    rapid_k         INTEGER,
    blitz           INTEGER,
    blitz_k         INTEGER,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_id, month),
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

-- Create indexes for efficient queries
CREATE INDEX rating_history_player_id_idx ON rating_history(player_id);
CREATE INDEX rating_history_month_idx ON rating_history(month);

-- Create composite indexes for rating searches
CREATE INDEX rating_history_standard_idx ON rating_history(standard) WHERE standard IS NOT NULL;
CREATE INDEX rating_history_rapid_idx ON rating_history(rapid) WHERE rapid IS NOT NULL;
CREATE INDEX rating_history_blitz_idx ON rating_history(blitz) WHERE blitz IS NOT NULL;