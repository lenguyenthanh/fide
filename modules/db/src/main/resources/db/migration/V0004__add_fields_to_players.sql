-- ADD open title and sex

CREATE TYPE other_title AS ENUM ('IA', 'FA', 'NA', 'IO', 'FT', 'FST', 'DI', 'NI');
CREATE TYPE sex AS ENUM ('M', 'F');

ALTER TABLE IF EXISTS players
Add column if not exists sex sex;
ALTER TABLE IF EXISTS players
Add column if not exists other_title other_title;
