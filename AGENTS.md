# CLAUDE.md

Project-specific guidance for the FIDE Lichess Typelevel-stack project.

## Issue Tracking

This project uses **bd (beads)** for issue tracking.
Run `bd prime` for workflow context, or install hooks (`bd hooks install`) for auto-injection.

**Quick reference:**
- `bd ready` - Find unblocked work
- `bd create "Title" --type task --priority 2` - Create issue
- `bd close <id>` - Complete work
- `bd dolt push` - Push beads to remote

For full workflow details: `bd prime`

## Claude session rules

- Always save any plan/optimization session in `<project-root>/.claude/docs/` directory instead of just writing in the console
- Omit `Co-Authored-By` lines from git commits


## Skills

Load these skills **before writing, planning or reviewing any Scala code**:

- `/scala3-fp` — General Scala 3/FP standards
- `/cats-effect-io` and `/cats-effect-resource` — cats-effect best practices
- `/cats-mtl-typed-errors` — typed error handling with cats-mtl
- `/scala-sbt` — SBT and Metals workflow

## Modules Structure

```
types (standalone)
  ↓
domain → types
  ↓
db → domain, types
api → types
crawler → domain, db
  ↓
backend → api, domain, db, crawler
gatling (independent, tests backend via HTTP)
```

### types (`modules/types`)
Opaque/refined domain types using Iron. `FederationId`, `Rating`, `PlayerId`, `BirthYear`, `PageSize`, `PageNumber`, `NonEmptySet`. No dependencies — foundation for all modules.

### api (`modules/api`)
Smithy4s code generation and refinement providers. Bridges Smithy API specs with Iron types so generated code validates at (de)serialization boundaries. Depends on `types`.

### domain (`modules/domain`)
Core domain models and enumerations. `Title`/`OtherTitle`/`Gender` enums, `PlayerInfo`/`CrawlPlayer`/`HistoricalPlayer`/`Federation`/`FederationSummary` case classes, query models (`SortBy`, `Order`, `Sorting`, `Pagination`, `RatingRange`, `PlayerFilter`). Depends on `types`.

### db (`modules/db`)
Persistence layer with Skunk (PostgreSQL). `Db` trait (upsert, query, filter players/federations), `KVStore` (key-value metadata), `Health` (connectivity probe), `DbResource` (connection pool + migrations via Dumbo), Iron-Skunk codec integration. Depends on `domain`.

### crawler (`modules/crawler`)
FIDE data synchronization. Downloads `players_list.zip` from `ratings.fide.com`, decompresses, parses fixed-width text into `CrawlPlayer`, appends change events to `PlayerEventDb`. `Syncer` detects updates via `Last-Modified` header vs `KVStore`. Configurable chunk size and concurrency. Depends on `domain`, `db`.

### backend (`modules/backend`)
HTTP server and orchestration. Ember server with Smithy4s routes (`PlayerService`, `FederationService`, `HealthService`), Swagger docs, middleware (auto-slash, 60s timeout). Services query `Db` and transform to API DTOs. `CrawlerJob` runs crawler on a background schedule. Config via Ciris. Entry point: `App extends IOApp.Simple`. Depends on `api`, `domain`, `db`, `crawler`.

### gatling (`modules/gatling`)
Load testing simulations (Gatling). Warmup (2k users), Stress (12k users), and Capacity (incremental ramp) scenarios against `localhost:9669/api`. Independent of other modules.

### Project-specific opaque types
Defined in `core/domain/Ids.scala` and `core/domain/Types.scala`.
Prefer to use Iron to define new type than opaque

### Proactive code smell detection

Scope follows the compiler iteratively:
(1) find smell in current file
(2) fix it
(3) compile → if it fails because other files import the changed symbol, fix those too
(4) repeat until compilation passes. If **reading unrelated code** (not in the compilation chain) and spotting a violation — **add it to the smell list** (see Code Smell Tracking below), do not fix. This applies to all rules: type safety, error handling, control flow, naming, logging, etc.

### Code Smell Tracking

When spotting code smells in **unrelated code** (not in the current compilation chain), add them to the persistent smell list file at `<project-root>/.claude/memory/code_smells.md` instead of just warning in the response.

**Rules:**
- **Max 10 entries.** If adding an 11th, delete the oldest entry (FIFO eviction).
- **Prioritize by severity.** Most critical smells first (silent error swallowing > naming inconsistency).
- **At end of every task**, remind the user about pending smells and suggest fixing them in a dedicated session.
- **Each entry** includes: file path, line number, rule violated, brief description.
- **Remove entries** when the smell is fixed (either by the user or in a subsequent session).

## Plan Before Implementing

For non-trivial tasks, generate a plan before writing code:
1. Identify affected modules and files.
2. List the changes needed in each file.
3. Note any schema/migration impacts.
4. Confirm the plan before implementing.

After implementing a group of related changes, review for:
- Correctness (does it compile and pass tests?)
- Consistency (does it follow existing patterns in the codebase?)
- Simplicity (is there a simpler way to achieve the same result?)

### Fixing a Bug

1. Write a failing test reproducing the bug
2. Verify the test fails, apply fix, clean again, verify all tests pass
