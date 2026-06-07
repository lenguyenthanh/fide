-- Set DEFAULT on player_info.id so batch INSERTs can omit it
ALTER TABLE player_info ALTER COLUMN id SET DEFAULT nextval('player_info_id_seq');
