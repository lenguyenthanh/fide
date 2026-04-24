package fide.cli
package test

import com.monovore.decline.*
import weaver.*

import java.time.LocalDate

/** CLI smoke tests for the `live-ratings backfill` subcommand parser.
  * See design CLI decision #5 (nested subcommand structure).
  */
object BackfillConfigSuite extends SimpleIOSuite:

  private def parse(args: List[String]): Either[String, BackfillCliOpts] =
    Command("backfill", "test")(BackfillConfig.opts).parse(args) match
      case Right(opts) => Right(opts)
      case Left(help)  => Left(help.toString)

  pureTest("parses no args: defaults (no --since, no --tour-id, no --dry-run)"):
    parse(Nil) match
      case Left(err) => failure(s"expected success, got: $err")
      case Right(opts) =>
        expect(opts.since.isEmpty) and
          expect(opts.tourId.isEmpty) and
          expect(!opts.dryRun)

  pureTest("parses --since YYYY-MM-DD as UTC LocalDate"):
    parse(List("--since", "2026-04-01")) match
      case Left(err) => failure(s"expected success, got: $err")
      case Right(opts) =>
        expect(opts.since.contains(LocalDate.of(2026, 4, 1)))

  pureTest("rejects malformed --since"):
    parse(List("--since", "not-a-date")) match
      case Left(_)  => success
      case Right(_) => failure("expected parse failure for invalid date")

  pureTest("parses --tour-id"):
    parse(List("--tour-id", "abc12345")) match
      case Left(err) => failure(s"expected success, got: $err")
      case Right(opts) =>
        expect(opts.tourId.contains("abc12345"))

  pureTest("parses --dry-run flag"):
    parse(List("--dry-run")) match
      case Left(err) => failure(s"expected success, got: $err")
      case Right(opts) =>
        expect(opts.dryRun)

  pureTest("parses combined: --since + --tour-id + --dry-run"):
    parse(List("--since", "2026-04-01", "--tour-id", "abc12345", "--dry-run")) match
      case Left(err) => failure(s"expected success, got: $err")
      case Right(opts) =>
        expect(opts.since.contains(LocalDate.of(2026, 4, 1))) and
          expect(opts.tourId.contains("abc12345")) and
          expect(opts.dryRun)
