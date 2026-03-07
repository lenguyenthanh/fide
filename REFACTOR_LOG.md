# Refactor Implementation Log

Documents deviations, decisions, and notes as the plan is implemented.

## Phase 1: Migration V0012

**File**: `modules/db/src/main/resources/db/migration/V0012__player_info_and_history.sql`

### Step 1.1: Create `player_info` table
- Plan: id, name, sex, birth_year, created_at, updated_at + GIN index + updated_at trigger
- Implementation: Follows plan exactly. Reuses existing `set_updated_at()` trigger function from V0001.
- Added both GIN trigram index (for `%` similarity search) and DESC index on name (for sorting).

### Step 1.2: Create `player_history` table
- Plan: (player_id, month) composite PK, snapshot columns, indexes
- Implementation: Follows plan exactly.
- `month` is `SMALLINT` (max 32767 covers year ~4700).
- FKs to both `player_info(id)` and `federations(id)`.
- DESC indexes on standard, rapid, blitz for sort queries.

### Step 1.3: Populate from `players`
- Plan: INSERT INTO player_info SELECT ...; INSERT INTO player_history SELECT ... with synthetic month
- Implementation: Follows plan. Month computed as `((EXTRACT(YEAR FROM NOW()) - 1970) * 12 + EXTRACT(MONTH FROM NOW()) - 1)::smallint` at migration time.
- No deviation.

### Step 1.4: Drop federation views
- Plan: trigger -> materialized view -> views (reverse dependency order)
- Implementation: Follows plan exactly. Drop order:
  1. `refresh_federation_summary_trigger` (on cache table)
  2. `federations_summary` (materialized view)
  3. `federations_avg_top_10_ranking` (view)
  4. `federations_with_players_count_and_avg_rating` (view)

### Step 1.5: Drop `players` table
- Implementation: `DROP TABLE IF EXISTS players;` - cascades its indexes and triggers.

### Step 1.6: Create `player_current` view
- Plan: DISTINCT ON (player_id) ordered by month DESC
- Implementation: Follows plan exactly. Selects all snapshot columns from `player_history`.

### Step 1.7: Recreate federation views + trigger
- Plan: Same logic, reference `player_current` instead of `players`
- Implementation: Follows V0008 structure exactly, replacing `players` with `player_current AS pc`.
- **Deviation**: Federation summary indexes use `_v2` suffix to avoid name collisions with old indexes (which were dropped with the materialized view, but Flyway may track names).
- Recreated both ASC and DESC indexes (from V0008 and V0009 respectively).
- `refresh_federations_summary()` function was NOT dropped/recreated - it still exists from V0008 and just does `REFRESH MATERIALIZED VIEW CONCURRENTLY federations_summary`. Only the trigger was dropped and recreated.

## Phase 2: Domain.scala

**File**: `modules/domain/src/main/scala/Domain.scala`

- Removed `NewPlayer` case class.
- Added `NewPlayerInfo(id, name, gender, birthYear)` - 4 fields (static biographical data).
- Added `NewPlayerHistory(playerId, month, title, womenTitle, otherTitles, federationId, active, standard, standardK, rapid, rapidK, blitz, blitzK)` - 13 fields (monthly snapshot).
- Added `Month` object with `current: Short` helper.
- **No deviation from plan**. `PlayerInfo` (read model) unchanged.

## Phase 3: Db.scala

**File**: `modules/db/src/main/scala/Db.scala`

- `Db` trait: `upsert` signatures changed from `NewPlayer` to `(NewPlayerInfo, NewPlayerHistory, Option[NewFederation])`.
- Batch `upsert` changed from `List[(NewPlayer, Option[NewFederation])]` to `List[(NewPlayerInfo, NewPlayerHistory, Option[NewFederation])]`.
- Codecs: `newPlayer` replaced with `newPlayerInfo` (4 fields) + `newPlayerHistory` (13 fields, uses `int2` for month).
- `playerInfo` read codec: Column order matches the new `allPlayersFragment` SELECT but the codec type mapping is unchanged (same `PlayerInfo` case class).
- Upsert SQL: 3 prepared statements per transaction (federations -> player_info -> player_history), following FK dependency chain.
- `between` helper: Changed from hardcoded `p.` prefix to accepting `(table, column, range)` or `(qualifiedCol, min, max)`.
- **Deviation**: `birth_year` filter uses `between("pi.birth_year", ...)` directly instead of `between("pi", "birth_year", ...)` since the overload resolution was cleaner with the fully qualified column name.
- All filter fragments updated: `pi.` for name/sex/birth_year, `pc.` for title/women_title/other_titles/federation_id/active/standard/rapid/blitz.
- `sortingFragment` uses `qualifiedColumn(sortBy)` helper: Name/BirthYear -> `pi.`, Standard/Rapid/Blitz -> `pc.`.
- `countPlayers` base query: `FROM player_info AS pi LEFT JOIN player_current AS pc ON pi.id = pc.player_id`.
- `allPlayersFragment`: 3-way JOIN: `player_info AS pi LEFT JOIN player_current AS pc LEFT JOIN federations AS f ON pc.federation_id`.

## Phase 4: Crawler (Downloader.scala, Crawler.scala)

**Files**: `modules/crawler/src/main/scala/Downloader.scala`, `modules/crawler/src/main/scala/Crawler.scala`

- `Downloader.fetch` type: `fs2.Stream[IO, (NewPlayerInfo, NewPlayerHistory, Option[NewFederation])]`.
- `parseLine` now takes `month: Short` parameter. `Month.current` computed once in `fetch` before the stream starts.
- `parsePlayer` returns `Option[(NewPlayerInfo, NewPlayerHistory)]` - splits the parsed line into info + history.
- Federation lookup uses `history.federationId` and `info.id` (was `player.federationId` and `player.id`).
- `Crawler.fetchAndSave`: Changed `db.upsert` call to pass triple. Used explicit `xs => db.upsert(xs)` lambda for clarity (was point-free `db.upsert`).
- **No other deviations from plan**.

## Deadlock Fix (Post Phase 4)

**Problem**: Running the crawler caused `ERROR 40P01 DeadlockDetected` on `INSERT INTO federations ... ON CONFLICT DO NOTHING`. With 40 concurrent transactions each inserting overlapping federation sets while also holding locks on `player_info` and `player_history`, PostgreSQL detected deadlocks.

**Root cause**: The old code also inserted federations inside each chunk's transaction, but with only 1 table (`players`) the transactions were short. With 3 tables, transactions hold locks longer, making deadlocks far more likely across concurrent chunks that share federation IDs.

**Fix**: Separated federation upserts completely from player upserts.

### Db trait
- Removed federation from `upsert` signatures entirely.
- Added `upsertFederations(xs: List[NewFederation]): IO[Unit]` as a standalone operation.
- `upsert(info, history)` and `upsert(xs: List[(NewPlayerInfo, NewPlayerHistory)])` no longer touch `federations` table.
- Removed unused `Sql.upsertFederation` (singular).

### Crawler
- `fetchAndSave` now calls `upsertAllFederations` once before streaming player chunks.
- Uses `Federation.all` (the hardcoded map) to build the full federation list upfront.
- Added import for `Federation` and `NewFederation` in `Crawler.scala`.

### Downloader
- Stream type simplified: `fs2.Stream[IO, (NewPlayerInfo, NewPlayerHistory)]` (was triple with `Option[NewFederation]`).
- Federation lookup replaced with `warnUnknownFederation` — only logs, no longer produces `NewFederation` objects.

### Why this works
- Federations are a small, static set (~200 entries). Upserting them all once is fast and contention-free.
- Player chunk transactions now only touch `player_info` and `player_history`, which have non-overlapping PKs across chunks, so no deadlocks.

## Phase 5: Tests

### DbSuite.scala
**File**: `modules/db/src/test/scala/DbSuite.scala`

- Replaced `NewPlayer` fixtures with `NewPlayerInfo` + `NewPlayerHistory` pairs.
- Added `testMonth: Short = Month.current` for constructing test history records.
- Replaced `transform` extension (used ducktape to convert `PlayerInfo -> NewPlayer`) with `toNewPlayerInfo` and `toNewPlayerHistory` extensions (manual construction, no ducktape needed for these).
- `ducktape` import kept - still used for `FederationInfo.to[NewFederation]`.
- Test data: `newPlayers` changed from `List[(NewPlayer, Option[NewFederation])]` to `List[(NewPlayerInfo, NewPlayerHistory)]`.
- Added `upsertWithFed` / `upsertWithFeds` test helper extensions on `Db` to upsert federations before players.
- All test assertions updated to use `toNewPlayerInfo`/`toNewPlayerHistory` instead of `transform`.

### ParserTest.scala
**File**: `modules/crawler/src/test/scala/ParserTest.scala`

- `parse` helper replaced with `parseHistory` that extracts `NewPlayerHistory` from the pair.
- Tests access `.otherTitles`, `.active`, `.standard` on `NewPlayerHistory` (same field names, was on `NewPlayer`).
- Added `testMonth` and `Month` import.

### DownloaderTest.scala
**File**: `modules/crawler/src/test/scala/DownloaderTest.scala`

- Stream now emits `(NewPlayerInfo, NewPlayerHistory)`. Test extracts `_._2.federationId` to check for unknown federations.

### Test execution
- All modules compile cleanly (main + test).
- Integration tests (DbSuite, KVStoreSuite, HealthSuite) fail with testcontainers DNS/InputStream errors - **pre-existing issue** on main branch (verified by running tests on clean main).
