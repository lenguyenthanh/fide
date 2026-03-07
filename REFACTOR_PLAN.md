# Refactoring Plan: `player_info` + Monthly `player_history`

## Goal

Split the `players` table into:
1. **`player_info`** - truly static biographical data (name, sex, birth year)
2. **`player_history`** - monthly snapshots of everything that changes: titles, federation, active status, and ratings

A **`player_current`** view exposes the latest month's snapshot per player via `DISTINCT ON`.

## What Goes Where

| `player_info` (static, 1 row/player) | `player_history` (monthly, 1 row/player/month) |
|---|---|
| id (PK) | player_id + month (composite PK) |
| name | title, women_title, other_titles |
| sex | federation_id (FK -> federations) |
| birth_year | active |
| created_at, updated_at | standard, standard_kfactor |
| | rapid, rapid_kfactor |
| | blitz, blitz_kfactor |
| | created_at |

## Phase 1: Database Migration (V0012)

**File**: `V0012__player_info_and_history.sql`

Order of operations:

1. Create `player_info` table + indexes + updated_at trigger
2. Create `player_history` table + indexes
3. Populate `player_info` from `players` (biographical columns)
4. Populate `player_history` from `players` (snapshot columns, synthetic current month)
5. Drop federation views: trigger -> materialized view -> views
6. Drop `players` table (cascades its indexes and triggers)
7. Create `player_current` view (DISTINCT ON player_id, ordered by month DESC)
8. Recreate federation views using `player_current` instead of `players`
9. Recreate federation summary refresh trigger

Month encoding: `SMALLINT`, formula `(year - 1970) * 12 + (month - 1)`.

## Phase 2: Domain Model Changes

**`modules/domain/src/main/scala/Domain.scala`**:

- Remove `NewPlayer`
- Add `NewPlayerInfo` (id, name, gender, birthYear)
- Add `NewPlayerHistory` (playerId, month, title, womenTitle, otherTitles, federationId, active, standard, standardK, rapid, rapidK, blitz, blitzK)
- Add `Month` helper object (current, fromYearMonth)
- Keep `PlayerInfo` (read model) unchanged

## Phase 3: Database Layer Changes

**`modules/db/src/main/scala/Db.scala`**:

- `Db` trait: `upsert` signature changes to accept `(NewPlayerInfo, NewPlayerHistory, Option[NewFederation])`
- Codecs: `newPlayerInfo` (4 fields) + `newPlayerHistory` (13 fields)
- Upsert SQL splits into 3 statements per transaction (FK order):
  1. `INSERT INTO federations ... ON CONFLICT DO NOTHING`
  2. `INSERT INTO player_info ... ON CONFLICT (id) DO UPDATE SET name, sex, birth_year`
  3. `INSERT INTO player_history ... ON CONFLICT (player_id, month) DO UPDATE SET ...`
- Read queries: `allPlayersFragment` JOINs `player_info AS pi` + `player_current AS pc` + `federations AS f`
- Filter prefix mapping:
  - `pi.`: name, sex, birth_year
  - `pc.`: title, women_title, other_titles, federation_id, active, standard, rapid, blitz
- Sorting prefix mapping:
  - Name, BirthYear -> `pi.`
  - Standard, Rapid, Blitz -> `pc.`
- `countPlayers`: base table `player_info AS pi LEFT JOIN player_current AS pc`

## Phase 4: Crawler Changes

**`modules/crawler/src/main/scala/Downloader.scala`**:

- `fetch` type: `fs2.Stream[IO, (NewPlayerInfo, NewPlayerHistory, Option[NewFederation])]`
- `parsePlayer` returns `Option[(NewPlayerInfo, NewPlayerHistory)]`
- Month: compute `Month.current` once, pass into parsing

**`modules/crawler/src/main/scala/Crawler.scala`**:

- `db.upsert` passes the new triple

## Phase 5: API Layer (No Changes)

- Smithy spec unchanged
- `PlayerServiceImpl` / `PlayerTransformers` unchanged
- `GetPlayerByIdOutput` unchanged - same combined data, different internal source

## Files to Modify

| File | Change |
|------|--------|
| `V0012__player_info_and_history.sql` | **New** - migration |
| `Domain.scala` | `NewPlayer` -> `NewPlayerInfo` + `NewPlayerHistory` + `Month` helper |
| `Db.scala` | New codecs, split upsert, updated queries/filters/sorting prefixes |
| `Downloader.scala` | Parse into `(NewPlayerInfo, NewPlayerHistory)`, pass month |
| `Crawler.scala` | Adjust `db.upsert` call signature |
| `DbSuite.scala` | Update fixtures and assertions |

**No changes needed**: Smithy specs, `service.player.scala`, `service.federation.scala`, `Models.scala`, `KVStore.scala`, `Syncer.scala`, `UpdateChecker.scala`, `CrawlerJob.scala`, `app.config.scala`
