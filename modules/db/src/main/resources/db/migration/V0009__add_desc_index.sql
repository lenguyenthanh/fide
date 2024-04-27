-- Add desc indexes for players
CREATE INDEX players_name_desc_idx ON players(name DESC NULLS LAST);
CREATE INDEX players_standard_desc_idx ON players(standard DESC NULLS LAST);
CREATE INDEX players_rapid_desc_idx ON players(rapid DESC NULLS LAST);
CREATE INDEX players_blitz_desc_idx ON players(blitz DESC NULLS LAST);

-- Add desc indexes for federations_summary
CREATE INDEX fed_sum_avg_rating_desc_idx ON federations_summary(avg_rating DESC NULLS LAST);
CREATE INDEX fed_sum_avg_standard_desc_idx ON federations_summary(avg_standard DESC NULLS LAST);
CREATE INDEX fed_sum_avg_rapid_desc_idx ON federations_summary(avg_rapid DESC NULLS LAST);
CREATE INDEX fed_sum_avg_blitz_desc_idx ON federations_summary(avg_blitz DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_standard_desc_idx ON federations_summary(avg_top_standard DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_rapid_desc_idx ON federations_summary(avg_top_rapid DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_blitz_desc_idx ON federations_summary(avg_top_blitz DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_standard_rank_desc_idx ON federations_summary(avg_top_standard_rank DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_rapid_rank_desc_idx ON federations_summary(avg_top_rapid_rank DESC NULLS LAST);
CREATE INDEX fed_sum_avg_top_blitz_rank_desc_idx ON federations_summary(avg_top_blitz_rank DESC NULLS LAST);
CREATE INDEX fed_sum_standard_players_desc_idx ON federations_summary(standard_players DESC NULLS LAST);
CREATE INDEX fed_sum_rapid_players_desc_idx ON federations_summary(rapid_players DESC NULLS LAST);
CREATE INDEX fed_sum_blitz_players_desc_idx ON federations_summary(blitz_players DESC NULLS LAST);
CREATE INDEX fed_sum_players_desc_idx ON federations_summary(players DESC NULLS LAST);
