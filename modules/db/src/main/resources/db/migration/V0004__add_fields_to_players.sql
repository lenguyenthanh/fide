-- ADD open title and sex

CREATE TYPE other_title AS ENUM ('IA', 'FA', 'NA', 'IO', 'FT', 'FI', 'FST', 'DI', 'NI', 'SI', 'LSI');
CREATE TYPE sex AS ENUM ('M', 'F');

ALTER TABLE IF EXISTS players
Add column if not exists sex sex default null;
ALTER TABLE IF EXISTS players
Add column if not exists other_title other_title default null;
