# CLAUDE.md

Guidance for AI agents generating Scala 3 code in this Typelevel-stack project.

## MCP — Always verify APIs via MCP before using them

Metals MCP at `.metals/mcp.json`. Query definitions/types/symbols, check compilation errors before running sbt. Fix MCP issues before running sbt to avoid long feedback loops.

## Global Rules

 - **Always use `sbt --client`** — never bare `sbt`. Connects to running server instead of launching new JVM.
 - **Redirect sbt output**: `sbt --client "module/clean ; test-jvm-2_13" 2>&1 | tee /tmp/sbt-output.txt`
   Then inspect with `grep`/`tail`/`head`. Only re-run sbt if code was modified.
 - Only run `sbt compile`/`sbt test` after MCP shows no compilation issues.
 - After code changes: `sbt lint` (scalafmt + scalafix), then `sbt test`.
 - **Do not add yourself as co-author** — no `Co-Authored-By: Claude ...` in commits.

### Tool references

**Override Grep with Metals MCP when the question is "what does the compiler resolve this to?"**

Grep is the default and works for most searches. But it fails silently on these Scala-specific scenarios — use Metals instead:

| Scenario | Tool |
|---|---|
| What type is this expression / what does it return? | `mcp__metals__inspect` |
| Which given/implicit is resolved at this call site? | `mcp__metals__inspect` |
| Which overloaded method is called here? | `mcp__metals__inspect` |
| What's the underlying type of an opaque type? | `mcp__metals__inspect` |
| What does a wildcard import bring into scope? | `mcp__metals__inspect` |
| Who calls this method / all implementations of a trait? (semantic, not textual) | `mcp__metals__get-usages` |

Other Metals tools: `glob-search` (find symbols by name), `get-docs` (ScalaDoc), `compile-file` (single-file compile check), `list-modules`, `list-scalafix-rules`.

**Signal to switch:** When you grep and get 10+ candidates with no way to disambiguate — that means you need Metals, not a better regex. Fall back to Grep/Glob for non-Scala files, string literals, config values, SQL, or when Metals is unavailable.

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
Core domain models and enumerations. `Title`/`OtherTitle`/`Gender` enums, `PlayerInfo`/`NewPlayer`/`Federation`/`FederationSummary` case classes, query models (`SortBy`, `Order`, `Sorting`, `Pagination`, `RatingRange`, `PlayerFilter`). Depends on `types`.

### db (`modules/db`)
Persistence layer with Skunk (PostgreSQL). `Db` trait (upsert, query, filter players/federations), `KVStore` (key-value metadata), `Health` (connectivity probe), `DbResource` (connection pool + migrations via Dumbo), Iron-Skunk codec integration. Depends on `domain`.

### crawler (`modules/crawler`)
FIDE data synchronization. Downloads `players_list.zip` from `ratings.fide.com`, decompresses, parses fixed-width text into `NewPlayer`, bulk-upserts via `Db`. `Syncer` detects updates via `Last-Modified` header vs `KVStore`. Configurable chunk size and concurrency. Depends on `domain`, `db`.

### backend (`modules/backend`)
HTTP server and orchestration. Ember server with Smithy4s routes (`PlayerService`, `FederationService`, `HealthService`), Swagger docs, middleware (auto-slash, 60s timeout). Services query `Db` and transform to API DTOs. `CrawlerJob` runs crawler on a background schedule. Config via Ciris. Entry point: `App extends IOApp.Simple`. Depends on `api`, `domain`, `db`, `crawler`.

### gatling (`modules/gatling`)
Load testing simulations (Gatling). Warmup (2k users), Stress (12k users), and Capacity (incremental ramp) scenarios against `localhost:9669/api`. Independent of other modules.

## Functional Programming Standards

- **No mutable state**: avoid `var`, mutable collections, and `return`.
- **Prefer immutable case classes** over builder patterns.
- If mutability is unavoidable, scope it inside a method — never as class fields.
- Minimize side-effects; push them to the edges (entry points).
- Use ADTs (`enum`) for modeling closed sets of variants.

## Scala 3 Syntax

- **Braceless syntax** — the project uses `rewrite.scala3.removeOptionalBraces = yes`.
- Use Scala 3 `enum`, `given`/`using`, `extension`, `opaque type`, and `derives` where appropriate.
- Use `for`/`yield` (not `flatMap` chains) for sequencing effects — this is the idiomatic pattern in this codebase.
- Prefer `match` expressions over nested `if`/`else`.

## Code Style

- Braceless syntax — no curly braces for `if`, `match`, `for`, class/object bodies.
- `maxColumn = 110`.
- Wildcard imports: `import cats.syntax.all.*`
- `align.preset = none` — do not align on `=` or `=>`.

### Code Style: Flat `for`-comprehensions

Prefer flat `for`/`yield` over nested `match`/`case` inside effectful blocks. Lift `Either`/`Option` into `F` so the `for` stays linear:

```scala
// BAD — nested match in MID-CHAIN breaks the for-comprehension flow
for
  result <- service.doSomething(...)
  value  <- result match  // BAD: match in middle, more steps follow
    case Right(v)  => v.pure[F]
    case Left(err) => Sync[F].raiseError(err)
  next   <- process(value)
  response <- Ok(next.asJson)
yield response

// ALSO BAD — .flatMap with case inside for-comprehension
for
  body <- req.req.as[Body]
  result <- service.getItem(id).flatMap {
    case Some(item) => Ok(item.asJson)
    case None       => NotFound(...)
  }
yield result

// GOOD — plain match at TERMINAL position (pure response mapping, no side effects)
for
  body   <- req.req.as[Body]
  result <- service.doSomething(body)
  response <- result match
    case Right(value) => Ok(value.asJson)
    case Left(err)    => BadRequest(err.asJson)
yield response
```

### Code style rules
- **No fully-qualified names in code.** Always use imports.
- **Context bounds: use `{A, B, C}` syntax** (Scala 3.6 aggregate bounds), not colon-separated.
- **Opaque types for domain values.** AI writes 90%+ of code, so write-cost is near zero while compile-time safety is free. Use opaque types with smart constructors for all entity IDs, constrained strings, and bounded numbers. Defined in `core/domain/Ids.scala` and `core/domain/Types.scala`.
- **Type-level constraints flow E2E.** Encode invariants in types (opaque types, `NonEmptyList`, refined types) and propagate them through **all** layer signatures: route → service → repository. Never downgrade a constraint to a weaker type and re-validate internally — that hides the requirement from callers and defeats compile-time safety. Unwrap/weaken only at the true system boundary: SQL interpolation, Java SDK calls, job parameter serialization.
- **`.toString` over `.value.toString`.** Opaque types erase at runtime, so `s"...$opaqueId"` and `opaqueId.toString` just work — no need to unwrap first.
- **`NonEmptyList` over `List` + `.get`/`.head`.** When a method logically requires non-empty input (batch embeddings, `IN` clauses, etc.), use `NonEmptyList[T]` in the **signature** — including repository methods — instead of `List[T]` with a runtime `.toNel.get` or `.head`. Callers use `NonEmptyList.fromList` to handle the empty case at the call site.
- **No premature helpers.** If the logic can be composed from <5 Scala/cats operators, always inline at call site — never extract a helper. If >=5 operators, **ask the user** before extracting (in plan mode or popup dialog). When consensus is reached on a new helper, add/link it in this document so future sessions know to use it. **Always use helpers already listed here** (e.g., `AsyncOps`) — don't expand them inline. Before writing any new helper, search the codebase for existing ones that do the same thing.
- **Generic over specific (stdlib/cats only).** Prefer composing well-tested Scala/cats operators generically over type-specific. "Generic" means leveraging stdlib type classes, not extracting custom helper functions — those still follow the <5 operator rule.
- **Proactive naming review.** When modifying code, flag misleading, stale, or inconsistent names to the user. **Scope follows the compiler iteratively** — same as smell detection: start with changed files, then follow compilation errors outward. For **internal names** (classes, properties, methods) — recommend renaming directly. For **external names** (request/response DTOs, DB-serialized JSONB fields) — suggest the better name but note migration implications.
- **Proactive code smell detection.** Scope follows the compiler iteratively: (1) find smell in current file, (2) fix it, (3) compile → if it fails because other files import the changed symbol, fix those too, (4) repeat until compilation passes. If **reading unrelated code** (not in the compilation chain) and spotting a violation — **add it to the smell list** (see Code Smell Tracking below), do not fix. This applies to all rules: type safety, error handling, control flow, naming, logging, etc.

### Error Handling

- **No error swallowing.** Unless the business scenario *explicitly* requires a default value, using `.getOrElse`, blanket `try`/`catch`, `.recover`, `.handleErrorWith`, or `.orElse` to silently discard failures is forbidden. Every error must be surfaced — either raised via `Raise` or propagated in the type.
- **Prefer cats-mtl typed errors over `Either`.** Use `Raise[F, E]` / `Handle[F, E]` to short-circuit errors in `F` directly, keeping for-comprehensions flat. Avoid `IO[Either[E, A]]` return types — they force callers to unwrap and nest. Reserve `Either` for pure validation logic or values that cross serialization boundaries.

```scala
// BAD — Either in return type, callers must unwrap
def fetchUser(id: UserId): IO[Either[AppError, User]]

// GOOD — error raised in F, callers just flatMap
def fetchUser[F[_]: {Async, Raise[AppError]}](id: UserId): F[User]
```

- **Trusted vs untrusted data paths.** Internal data (config, DB-persisted values, intra-service calls) is trusted — failures are bugs, raise directly (`MonadThrow`). External data (user input, third-party APIs) is untrusted — capture failures via `Raise` so callers `Handle` them explicitly at the boundary.
- **Trust the compiler.** What the signature declares, trust unconditionally. Do not defensively re-validate what the type system already guarantees (e.g., don't `require(nel.nonEmpty)` on a `NonEmptyList`, don't null-check an opaque type). Every act of distrust costs tokens exponentially — the agent opens the implementation, reads dependents, context bloats, and subsequent steps degrade.
- **Signature completeness.** Every function must honestly declare its effects (`IO`/`F[_]`), failure modes (via `Raise[F, E]` constraint or `Option`), and use domain types for parameters. A signature like `def fetchUser(id: String): User` is dishonest — it hides effects and error paths. Prefer `def fetchUser[F[_]: {Async, Raise[AppError]}](id: UserId): F[User]`.

### NoOp Service Implementations

When providing stub/noop implementations of service traits:
- **Data-related operations MUST fail** — raise the error or return a failed effect. Silent success would mask missing wiring.
- **Data-irrelevant operations CAN succeed** — metrics, logging, telemetry noops return `().pure[F]` since skipping them is safe.

### Code Smell Tracking

When spotting code smells in **unrelated code** (not in the current compilation chain), add them to the persistent smell list file at `<project-root>/memory/code_smells.md` instead of just warning in the response.

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
3. Note any schema/migration impacts (Smithy, ES mappings).
4. Confirm the plan before implementing.

After implementing a group of related changes, review for:
- Correctness (does it compile and pass tests?)
- Consistency (does it follow existing patterns in the codebase?)
- Simplicity (is there a simpler way to achieve the same result?)

## Things to Avoid

- Do NOT use `Future`, `Await`, `Thread.sleep`, or blocking calls on the IO thread pool.
- Do NOT use Ox, ZIO, or direct-style concurrency libraries — this project is cats-effect.
- Do NOT manually define ES field mappings — they are generated from Smithy schemas.
- Do NOT reformat snapshot JSON files (`elastic/src/test/resources/snapshot/mappings/`).
- Do NOT wrap `Option[Boolean]` in `Some()` for Smithy trait properties.


## Rule Hierarchy — Four Layers of Constraint

Not all rules are equal. When adding or interpreting rules in this file, classify them by enforcement layer:

| Layer | Enforced by | Examples | Agent action |
|---|---|---|---|
| **1. Compiler** | Type system, exhaustiveness, opaque types | `Raise[F, E]` in signature, sealed trait match | No rule needed — compiler rejects violations |
| **2. Actionable rules** | This file — clear if/then criteria | "No error swallowing", "NonEmptyList over List + .head" | Follow unconditionally; violations are code smells |
| **3. Process constraints** | Filesystem as cross-session memory | Code smell tracking in `code_smells.md` | Agent discovers → records → human prioritizes |
| **4. Advisory** | Agent suggests, human decides | "Consider adding runtime assertion here", deploy impact | Flag to user, do not act unilaterally |

Push constraints **up** whenever possible: prefer a compiler-enforced type over an actionable rule, prefer an actionable rule over an advisory suggestion.

## Rule Quality Standard

Rules in this file must be precise enough that the agent never needs to read extra context to guess intent. Every ambiguity = extra file reads = token bloat = degraded reasoning.

```
// BAD — vague, requires judgment calls
"Always use tagless final style"
"Be pragmatic about error handling"

// GOOD — unambiguous if/then, no interpretation needed
"ParserService should be a typeclass constraint in the class constructor:
  class FileProcessor[F[_]: {Async, ParserService}]
NOT as a parameter:
  def process[F[_]](parser: ParserService[F])"
```

When writing new rules: lead with the concrete pattern (what to do), then the anti-pattern (what not to do), then rationale (why). If a rule can't be expressed as a clear if/then, it belongs in Layer 4 (advisory), not Layer 2.

### Fixing a bug

1. Write a failing test reproducing the bug
3. Verify the test fails, apply fix, clean again, verify all tests pass
